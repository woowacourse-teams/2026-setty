"""실제 훅을 실행하되 systemd/Nginx/HTTP/소켓 명령은 임시 호스트에서 모의한다."""

import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import unittest


DEPLOY_DIR = Path(__file__).resolve().parents[1]
REPO = DEPLOY_DIR.parents[1]

FAKE_COMMAND = r'''
import json, os, re, sys, time
from pathlib import Path
from urllib.parse import parse_qs, urlparse

command = Path(sys.argv[0]).name
args = sys.argv[1:]
state_path = Path(os.environ['MOCK_STATE'])
state = json.loads(state_path.read_text())
state['calls'].append([command, *args])

def finish(code=0, output=''):
    state_path.write_text(json.dumps(state))
    if output:
        print(output)
    sys.exit(code)

def configured_slot():
    ports = re.findall(r'server\s+127\.0\.0\.1:(808[01]);', Path(state['upstream']).read_text())
    return 'blue' if ports == ['8080'] else 'green' if ports == ['8081'] else None

def name_to_slot(service):
    return 'blue' if '@blue.' in service else 'green' if '@green.' in service else service

if command == 'flock':
    finish(1 if state.get('lock_busy') else 0)
if command == 'sleep':
    time.sleep(0.03)
    finish()
if command == 'nginx':
    finish(1 if configured_slot() == state['new_slot'] and state.get('invalid_candidate') else 0)
if command == 'systemctl':
    operation = args[0]
    service = next((a for a in args[1:] if a.endswith('.service')), '')
    slot = name_to_slot(service)
    if operation == 'is-active':
        finish(0 if state['active'].get(slot, False) else 3)
    if operation == 'is-enabled':
        finish(0 if state['enabled'].get(slot, False) else 1)
    if operation == 'show':
        if '--property=LoadState' in args:
            finish(output='loaded')
        if '--property=ActiveState' in args:
            finish(output='active' if state['active'].get(slot, False) else 'inactive')
        if '--property=WorkingDirectory' in args:
            finish(output=str(Path(state['root']) / 'slots' / slot))
        if '--property=ExecStart' in args:
            jar = Path(state['root']) / ('legacy' if state.get('wrong_service_jar') else 'slots') / slot / 'app.jar'
            finish(output=f'{{ path=/usr/bin/java ; argv[]=/usr/bin/java -jar {jar} ; }}')
        finish(1)
    if operation == 'reset-failed':
        finish()
    if operation == 'reload':
        desired = configured_slot()
        if desired == state['new_slot'] and state.get('reload_failure'):
            finish(1)
        if desired == state['old_slot'] and state['switch_count'] and state.get('rollback_reload_failure'):
            finish(1)
        if desired == state['new_slot'] and state.get('reload_no_effect'):
            finish()
        state['live_slot'] = desired
        if desired == state['new_slot']:
            state['switch_count'] += 1
        finish()
    if operation == 'start':
        if slot == state['new_slot'] and state.get('start_failure'):
            finish(1)
        state['active'][slot] = True
        finish()
    if operation == 'stop':
        state['active'][slot] = False
        finish()
    if operation in ('enable', 'disable'):
        state['enabled'][slot] = operation == 'enable'
        finish()
    finish(1)
if command == 'ss':
    port = int(re.search(r'sport\s*=\s*:(\d+)', ' '.join(args)).group(1))
    slot = 'blue' if port == 8080 else 'green'
    if 'established' in args:
        busy = slot in state.get('busy_slots', [])
    else:
        busy = state['active'].get(slot, False) or state.get('orphan_port') == port
    finish(output=f'0 0 127.0.0.1:{port} 127.0.0.1:50000' if busy else '')
if command == 'curl':
    url = urlparse(args[-1])
    probe = parse_qs(url.query).get('setty_deploy_probe', [''])[0]
    marker = f'deploy_probe={probe} ' if not state.get('missing_probe_logging') else ''
    if url.hostname != '127.0.0.1':
        if state['live_slot'] == state['new_slot']:
            state['new_smoke_count'] += 1
            if state.get('smoke_failure') or (state.get('observation_failure') and state['new_smoke_count'] >= 2):
                finish(22)
        if not state.get('external_bypasses_nginx'):
            slot = state['old_slot'] if state.get('external_stuck_on_old') else state['live_slot']
            address = '8080' if slot == 'blue' else '8081'
            with Path(state['access_log']).open('a') as log:
                log.write(f'127.0.0.1 "GET {url.path}" status=200 upstream=127.0.0.1:{address} {marker}request_time=0.001\n')
        finish()
    port = url.port or 80
    slot = state['live_slot'] if port == 80 else 'blue' if port == 8080 else 'green'
    healthy = state['active'].get(slot, False)
    if slot == state['new_slot'] and state.get('health_failure'):
        healthy = False
    if (slot == state['new_slot'] and state.get('failure_after_old_stop')
            and not state['active'][state['old_slot']]):
        healthy = False
    if not healthy:
        finish(22)
    if port == 80:
        address = '8080' if slot == 'blue' else '8081'
        with Path(state['access_log']).open('a') as log:
            log.write(f'127.0.0.1 "GET {url.path}" status=200 upstream=127.0.0.1:{address} {marker}request_time=0.001\n')
    finish(output='{"status":"UP"}')
finish(1)
'''


class BlueGreenDeploymentTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='setty-deploy-test-')
        self.addCleanup(self.temporary.cleanup)
        self.base = Path(self.temporary.name)
        self.host = self.base / 'host'
        self.bundle = self.base / 'bundle'
        self.bin = self.base / 'bin'
        self.bin.mkdir()
        self.host.mkdir()
        self.hooks = self.bundle / 'server/deploy/blue-green'
        self.hooks.parent.mkdir(parents=True)
        shutil.copytree(DEPLOY_DIR / 'blue-green', self.hooks)
        (self.bundle / 'app.jar').write_bytes(b'new-application-revision')
        (self.host / 'setty.env').write_text('SPRING_PROFILES_ACTIVE=dev\n')
        for slot, port in [('blue', 8080), ('green', 8081)]:
            directory = self.host / 'slots' / slot
            directory.mkdir(parents=True)
            (directory / 'app.jar').write_bytes(f'original-{slot}'.encode())
            (directory / 'slot.env').write_text(f'SPRING_PROFILES_ACTIVE=dev\nSERVER_PORT={port}\n')
        self.upstream = self.host / 'active-upstream.conf'
        self.access_log = self.host / 'access.log'
        self.access_log.touch()
        self.config = self.host / 'deploy.env'
        self.state_file = self.base / 'mock-state.json'
        fake = self.bin / 'fake.py'
        fake.write_text(f'#!{sys.executable}\n' + FAKE_COMMAND)
        fake.chmod(0o755)
        for name in ('systemctl', 'nginx', 'curl', 'ss', 'flock', 'sleep'):
            (self.bin / name).symlink_to(fake)
        self.env = {
            **os.environ,
            'PATH': f'{self.bin}:{os.environ["PATH"]}',
            'SETTY_DEPLOY_CONFIG': str(self.config),
            'MOCK_STATE': str(self.state_file),
            'APPLICATION_NAME': 'setty-backend',
            'DEPLOYMENT_GROUP_NAME': 'setty-backend-dev',
            'DEPLOYMENT_ID': 'd-test-1',
            'LC_ALL': 'C',
        }
        self.configure()

    def configure(self, active='blue', environment='dev', observe=0, **flags):
        new = 'green' if active == 'blue' else 'blue'
        port = 8080 if active == 'blue' else 8081
        self.upstream.write_text(f'upstream setty_backend {{\n    server 127.0.0.1:{port};\n}}\n')
        self.config.write_text('\n'.join([
            f'SETTY_DEPLOY_ENVIRONMENT={environment}',
            f'SETTY_DEPLOY_ROOT={self.host}',
            f'SETTY_NGINX_UPSTREAM={self.upstream}',
            f'SETTY_NGINX_ACCESS_LOG={self.access_log}',
            'SETTY_PROXY_HEALTH_URL=http://127.0.0.1/actuator/health',
            f'SETTY_SMOKE_URL=https://www.setty.cloud/{"dev/" if environment == "dev" else ""}api/listings?size=1',
            'SETTY_HEALTH_TIMEOUT_SECONDS=1',
            'SETTY_ROUTE_TIMEOUT_SECONDS=1',
            f'SETTY_OBSERVE_SECONDS={observe}',
            'SETTY_DRAIN_TIMEOUT_SECONDS=1',
            'SETTY_POLL_SECONDS=1',
            '',
        ]))
        for slot in ('blue', 'green'):
            file = self.host / 'slots' / slot / 'slot.env'
            file.write_text(re.sub(r'ACTIVE=\w+', f'ACTIVE={environment}', file.read_text()))
        self.state_file.write_text(json.dumps({
            'old_slot': active, 'new_slot': new, 'live_slot': active,
            'root': str(self.host), 'upstream': str(self.upstream), 'access_log': str(self.access_log),
            'active': {active: True, new: False, 'nginx.service': True, 'setty-backend.service': False},
            'enabled': {active: True, new: False, 'nginx.service': True, 'setty-backend.service': False},
            'switch_count': 0, 'new_smoke_count': 0, 'calls': [], **flags,
        }))

    def run_hook(self, name='deploy.sh', success=True):
        result = subprocess.run(['bash', str(self.hooks / name)], env=self.env,
                                capture_output=True, text=True, timeout=25)
        if success:
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        else:
            self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        return result

    def state(self):
        return json.loads(self.state_file.read_text())

    def record(self):
        return (self.host / 'deployments/d-test-1/state').read_text()

    def assert_restored(self, old='blue'):
        state = self.state()
        new = 'green' if old == 'blue' else 'blue'
        self.assertEqual(state['live_slot'], old)
        self.assertTrue(state['active'][old])
        self.assertTrue(state['enabled'][old])
        self.assertFalse(state['active'][new])
        self.assertFalse(state['enabled'][new])
        self.assertFalse((self.host / 'deployments/pending').exists())
        self.assertIn('phase=rolled_back', self.record())
        self.assertEqual((self.host / f'slots/{old}/app.jar').read_bytes(), f'original-{old}'.encode())

    def test_blue_to_green(self):
        self.run_hook()
        state = self.state()
        self.assertEqual(state['live_slot'], 'green')
        self.assertTrue(state['active']['green'])
        self.assertTrue(state['enabled']['green'])
        self.assertFalse(state['active']['blue'])
        self.assertFalse(state['enabled']['blue'])
        self.assertEqual((self.host / 'slots/blue/app.jar').read_bytes(), b'original-blue')
        self.assertEqual((self.host / 'slots/green/app.jar').read_bytes(), b'new-application-revision')
        self.assertFalse((self.host / 'deployments/pending').exists())
        self.assertIn('phase=completed', self.record())

    def test_green_to_blue(self):
        self.configure(active='green')
        self.run_hook()
        state = self.state()
        self.assertEqual(state['live_slot'], 'blue')
        self.assertFalse(state['active']['green'])
        self.assertTrue(state['active']['blue'])
        self.assertEqual((self.host / 'slots/green/app.jar').read_bytes(), b'original-green')

    def test_prod_uses_the_same_controller(self):
        self.configure(environment='prod')
        self.env['APPLICATION_NAME'] = 'setty-backend-prod'
        self.env['DEPLOYMENT_GROUP_NAME'] = 'setty-backend-prod'
        self.run_hook()
        self.assertEqual(self.state()['live_slot'], 'green')

    def test_preflight_does_not_mutate_services_or_jars(self):
        self.run_hook('preflight.sh')
        self.assertEqual(self.state()['live_slot'], 'blue')
        self.assertFalse((self.host / 'deployments').exists())
        self.assertEqual((self.host / 'slots/green/app.jar').read_bytes(), b'original-green')

    def test_start_failure(self):
        self.configure(start_failure=True)
        self.run_hook(success=False)
        self.assert_restored()

    def test_health_failure(self):
        self.configure(health_failure=True)
        self.run_hook(success=False)
        self.assert_restored()

    def test_invalid_nginx_candidate(self):
        self.configure(invalid_candidate=True)
        self.run_hook(success=False)
        self.assert_restored()

    def test_reload_failure(self):
        self.configure(reload_failure=True)
        self.run_hook(success=False)
        self.assert_restored()

    def test_successful_reload_without_route_change_is_detected(self):
        self.configure(reload_no_effect=True)
        self.run_hook(success=False)
        self.assert_restored()

    def test_external_api_failure_after_switch(self):
        self.configure(smoke_failure=True)
        self.run_hook(success=False)
        self.assert_restored()

    def test_failure_during_observation(self):
        self.configure(observe=1, observation_failure=True)
        self.run_hook(success=False)
        self.assert_restored()

    def test_external_success_must_reach_the_new_slot(self):
        self.configure(external_stuck_on_old=True)
        self.run_hook(success=False)
        self.assert_restored()

    def test_external_nginx_bypass_is_rejected_before_installation(self):
        self.configure(external_bypasses_nginx=True)
        self.run_hook(success=False)
        self.assertEqual((self.host / 'slots/green/app.jar').read_bytes(), b'original-green')

    def test_missing_probe_logging_is_rejected_before_installation(self):
        self.configure(missing_probe_logging=True)
        self.run_hook(success=False)
        self.assertEqual((self.host / 'slots/green/app.jar').read_bytes(), b'original-green')
        self.assertFalse(self.state()['active']['green'])

    def test_old_connections_are_not_terminated_on_drain_timeout(self):
        self.configure(busy_slots=['blue'])
        self.run_hook(success=False)
        self.assert_restored()
        calls = self.state()['calls']
        self.assertNotIn(['systemctl', 'stop', 'setty-backend@blue.service'], calls)

    def test_incomplete_drain_after_rollback_blocks_further_deployments(self):
        self.configure(smoke_failure=True, busy_slots=['green'])
        self.run_hook(success=False)
        self.assertEqual(self.state()['live_slot'], 'blue')
        self.assertTrue(self.state()['active']['green'])
        self.assertIn('phase=recovery_required', self.record())
        self.assertTrue((self.host / 'deployments/pending').exists())
        self.env['DEPLOYMENT_ID'] = 'd-test-2'
        self.run_hook(success=False)

    def test_rollback_failure_preserves_both_processes(self):
        self.configure(smoke_failure=True, rollback_reload_failure=True)
        self.run_hook(success=False)
        self.assertTrue(self.state()['active']['blue'])
        self.assertTrue(self.state()['active']['green'])
        self.assertIn('phase=recovery_required', self.record())
        self.assertTrue((self.host / 'deployments/pending').exists())

    def test_failure_after_retirement_restarts_old_slot_before_rollback(self):
        self.configure(failure_after_old_stop=True)
        self.run_hook(success=False)
        self.assert_restored()
        self.assertIn(['systemctl', 'start', 'setty-backend@blue.service'], self.state()['calls'])

    def test_existing_pending_deployment_is_preserved(self):
        directory = self.host / 'deployments'
        directory.mkdir()
        (directory / 'pending').write_text('d-previous\n')
        self.run_hook(success=False)
        self.assertEqual((directory / 'pending').read_text(), 'd-previous\n')
        self.assertEqual((self.host / 'slots/green/app.jar').read_bytes(), b'original-green')

    def test_lock_conflict_is_rejected(self):
        self.configure(lock_busy=True)
        self.run_hook(success=False)
        self.assertEqual((self.host / 'slots/green/app.jar').read_bytes(), b'original-green')

    def test_profile_mismatch_is_rejected(self):
        (self.host / 'slots/green/slot.env').write_text('SPRING_PROFILES_ACTIVE=prod\nSERVER_PORT=8081\n')
        self.run_hook(success=False)
        self.assertEqual((self.host / 'slots/green/app.jar').read_bytes(), b'original-green')

    def test_service_pointing_to_another_jar_is_rejected(self):
        self.configure(wrong_service_jar=True)
        self.run_hook(success=False)
        self.assertEqual((self.host / 'slots/green/app.jar').read_bytes(), b'original-green')

    def test_slot_port_mismatch_is_rejected(self):
        file = self.host / 'slots/green/slot.env'
        file.write_text(file.read_text().replace('8081', '8080'))
        self.run_hook(success=False)
        self.assertEqual((self.host / 'slots/green/app.jar').read_bytes(), b'original-green')

    def test_legacy_process_is_rejected(self):
        state = self.state()
        state['active']['setty-backend.service'] = True
        self.state_file.write_text(json.dumps(state))
        self.run_hook(success=False)
        self.assertEqual((self.host / 'slots/green/app.jar').read_bytes(), b'original-green')

    def test_running_inactive_slot_is_not_overwritten(self):
        state = self.state()
        state['active']['green'] = True
        self.state_file.write_text(json.dumps(state))
        self.run_hook(success=False)
        self.assertEqual((self.host / 'slots/green/app.jar').read_bytes(), b'original-green')

    def test_multiple_upstream_servers_are_rejected(self):
        self.upstream.write_text('upstream setty_backend {\nserver 127.0.0.1:8080;\nserver 127.0.0.1:8081;\n}\n')
        self.run_hook(success=False)

    def test_completed_deployment_is_idempotent(self):
        self.run_hook()
        before = sum(call[:2] == ['systemctl', 'start'] for call in self.state()['calls'])
        self.run_hook()
        after = sum(call[:2] == ['systemctl', 'start'] for call in self.state()['calls'])
        self.assertEqual(before, after)

    def test_completed_deployment_with_changed_jar_is_rejected(self):
        self.run_hook()
        (self.host / 'slots/green/app.jar').write_bytes(b'manually-replaced')
        self.run_hook(success=False)

    def test_waits_must_fit_hook_timeout(self):
        with self.config.open('a') as file:
            file.write('SETTY_OBSERVE_SECONDS=1200\n')
        self.run_hook(success=False)
        self.assertEqual((self.host / 'slots/green/app.jar').read_bytes(), b'original-green')

    def test_manifest_uses_archive_and_only_new_hooks(self):
        manifest = (REPO / 'appspec.yml').read_text()
        self.assertNotIn('ApplicationStop:', manifest)
        self.assertNotIn('files:', manifest)
        self.assertNotIn('/opt/setty/app', manifest)
        for script in re.findall(r'location:\s*(\S+)', manifest):
            self.assertTrue((REPO / script).is_file(), script)


if __name__ == '__main__':
    unittest.main()
