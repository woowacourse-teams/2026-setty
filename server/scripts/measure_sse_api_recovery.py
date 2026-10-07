#!/usr/bin/env python3
"""Measure an isolated Spring restart through SSE reconnect and list API recovery."""

from __future__ import annotations

import argparse
import datetime
import json
import os
import pathlib
import re
import subprocess
import sys
import threading
import time
import urllib.request

API_PROXY_SCOPE = 'isolated-api-client-proxy; screen-render-not-measured'
STATE_LOCK = threading.Lock()
CLIENT_STOP = threading.Event()
EVENT_CONNECTED = threading.Event()
STATE: dict[str, object] = {'active_run': 0, 'records': {}}


def now_iso() -> str:
    return datetime.datetime.now(datetime.timezone.utc).isoformat(timespec='milliseconds').replace('+00:00', 'Z')


def fetch_requests(base_url: str, token: str, timeout: float = 4.0) -> tuple[int, list[int], str]:
    request = urllib.request.Request(
        base_url + '/api/delivery/requests',
        headers={'Authorization': 'Bearer ' + token},
    )
    with urllib.request.urlopen(request, timeout=timeout) as response:
        requests = json.loads(response.read())
        return response.status, [item['deliveryId'] for item in requests], now_iso()


def wait_for(predicate, timeout: float) -> bool:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if predicate():
            return True
        time.sleep(0.025)
    return False


def write_record(path: pathlib.Path, record: dict[str, object]) -> None:
    record['failedAttempts'] = int(record['failedSseAttempts']) + int(record['failedClientListAttempts'])
    clean = {key: value for key, value in record.items() if not key.startswith('_')}
    with path.open('a', encoding='utf-8') as output:
        output.write(json.dumps(clean, ensure_ascii=False, separators=(',', ':')) + '\n')
        output.flush()
        os.fsync(output.fileno())


def make_record(run: int, log_path: pathlib.Path, timeout: float) -> dict[str, object]:
    return {
        'run': run,
        'scenario': 'server-restart-api-proxy',
        'scope': API_PROXY_SCOPE,
        'dbPreserved': True,
        'serverStopMethod': 'SIGKILL',
        'serverRestartedAt': now_iso(),
        'serverReadyAt': None,
        'sseReconnectedAt': None,
        'apiListMatchedAt': None,
        'screenMatchedAt': None,
        'serverRequestIds': None,
        'clientRequestIds': None,
        'locallyRejectedIds': [],
        'staleApiRequestIds': None,
        'missingApiRequestIds': None,
        'failedAttempts': 0,
        'failedSseAttempts': 0,
        'failedClientListAttempts': 0,
        'failedReadinessAttempts': 0,
        'sseReconnectSuccesses': 0,
        'timedOut': False,
        'timeoutSeconds': timeout,
        'serverLog': str(log_path),
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description='Repeatedly restart an isolated Spring process and measure SSE-triggered API list recovery.'
    )
    parser.add_argument('--jar', type=pathlib.Path, required=True, help='Built Spring Boot jar to restart')
    parser.add_argument('--token-file', type=pathlib.Path, required=True, help='File containing a disposable driver bearer token')
    parser.add_argument('--output', type=pathlib.Path, required=True, help='JSONL output path')
    parser.add_argument('--logs-dir', type=pathlib.Path, required=True, help='Directory for one Spring log per process start')
    parser.add_argument('--cycles', type=int, default=30, help='Number of restart samples (default: 30)')
    parser.add_argument('--port', type=int, default=18080, help='Dedicated local Spring port (default: 18080)')
    parser.add_argument('--ready-timeout', type=float, default=45.0, help='Seconds to wait for authenticated list API 200')
    parser.add_argument('--recovery-timeout', type=float, default=45.0, help='Seconds to wait for SSE reconnect and matching list')
    parser.add_argument('--java', default=None, help='Java executable; defaults to JAVA_HOME/bin/java or PATH java')
    args = parser.parse_args()
    if args.cycles < 1:
        parser.error('--cycles must be at least 1')
    if not 1 <= args.port <= 65535:
        parser.error('--port must be between 1 and 65535')
    return args


def main() -> int:
    args = parse_args()
    db_url = os.environ.get('SETTY_SSE_PROBE_DB_URL', '')
    db_user = os.environ.get('SETTY_SSE_PROBE_DB_USERNAME', '')
    db_password = os.environ.get('SETTY_SSE_PROBE_DB_PASSWORD', '')
    if not (db_url and db_user and db_password):
        print('SETTY_SSE_PROBE_DB_URL, SETTY_SSE_PROBE_DB_USERNAME, SETTY_SSE_PROBE_DB_PASSWORD are required.', file=sys.stderr)
        return 2
    match = re.fullmatch(
        r'jdbc:mysql://(?P<host>127\.0\.0\.1|localhost):\d+/(?P<database>[A-Za-z0-9_]+)(?:\?.*)?',
        db_url,
    )
    if match is None or not re.search(r'(probe|test)', match.group('database'), re.IGNORECASE):
        print('Refusing non-isolated datasource: use a local MySQL database whose name contains "probe" or "test".', file=sys.stderr)
        return 2
    if not args.jar.is_file():
        print(f'Spring Boot jar not found: {args.jar}', file=sys.stderr)
        return 2
    if not args.token_file.is_file():
        print(f'Driver token file not found: {args.token_file}', file=sys.stderr)
        return 2
    token = args.token_file.read_text(encoding='utf-8').strip()
    if not token:
        print('Driver token file is empty.', file=sys.stderr)
        return 2

    repo_root = pathlib.Path(__file__).resolve().parents[2]
    java = args.java or (
        str(pathlib.Path(os.environ['JAVA_HOME']) / 'bin/java') if os.environ.get('JAVA_HOME') else 'java'
    )
    base_url = f'http://127.0.0.1:{args.port}'
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.logs_dir.mkdir(parents=True, exist_ok=True)
    args.output.write_text('', encoding='utf-8')

    child_env = os.environ.copy()
    child_env['SPRING_DATASOURCE_URL'] = db_url
    child_env['SPRING_DATASOURCE_USERNAME'] = db_user
    child_env['SPRING_DATASOURCE_PASSWORD'] = db_password

    def start_server(label: str) -> tuple[subprocess.Popen, pathlib.Path]:
        log_path = args.logs_dir / f'{label}.log'
        with log_path.open('ab') as log:
            process = subprocess.Popen(
                [java, '-jar', str(args.jar.resolve()), '--server.address=127.0.0.1', f'--server.port={args.port}'],
                cwd=repo_root,
                env=child_env,
                stdin=subprocess.DEVNULL,
                stdout=log,
                stderr=subprocess.STDOUT,
            )
        return process, log_path

    def wait_ready(process: subprocess.Popen, record: dict[str, object]) -> bool:
        deadline = time.monotonic() + args.ready_timeout
        while time.monotonic() < deadline:
            if process.poll() is not None:
                return False
            try:
                status, ids, response_at = fetch_requests(base_url, token, timeout=1.5)
                if status == 200:
                    record['serverReadyAt'] = response_at
                    record['_serverRequestIds'] = ids
                    record['_serverReadyMono'] = time.monotonic()
                    record['serverRequestIds'] = ids
                    return True
            except Exception:
                record['failedReadinessAttempts'] = int(record['failedReadinessAttempts']) + 1
            time.sleep(0.05)
        return False

    def client_loop() -> None:
        delay = 1.0
        while not CLIENT_STOP.is_set():
            connected = False
            active_run = 0
            try:
                request = urllib.request.Request(
                    base_url + '/api/delivery/requests/events',
                    headers={'Authorization': 'Bearer ' + token, 'Accept': 'text/event-stream'},
                )
                with urllib.request.urlopen(request, timeout=20.0) as response:
                    if response.status != 200:
                        raise RuntimeError(f'SSE status {response.status}')
                    connected = True
                    delay = 1.0
                    EVENT_CONNECTED.set()
                    with STATE_LOCK:
                        active_run = int(STATE['active_run'])
                        records = STATE['records']
                        record = records.get(active_run)
                        if record is not None:
                            record['sseReconnectedAt'] = record.get('sseReconnectedAt') or now_iso()
                            record['sseReconnectSuccesses'] = int(record['sseReconnectSuccesses']) + 1
                    try:
                        status, ids, response_at = fetch_requests(base_url, token, timeout=8.0)
                        with STATE_LOCK:
                            record = STATE['records'].get(active_run)
                            if record is not None:
                                record['clientListResponseAt'] = record.get('clientListResponseAt') or response_at
                                record['clientRequestIds'] = ids
                                record['clientListStatus'] = status
                                server_ids = record.get('serverRequestIds')
                                if server_ids is not None:
                                    expected = set(server_ids) - set(record['locallyRejectedIds'])
                                    received = set(ids)
                                    record['staleApiRequestIds'] = sorted(received - expected)
                                    record['missingApiRequestIds'] = sorted(expected - received)
                                    if received == expected:
                                        record['apiListMatchedAt'] = response_at
                    except Exception as error:
                        with STATE_LOCK:
                            record = STATE['records'].get(active_run)
                            if record is not None:
                                record['failedClientListAttempts'] = int(record['failedClientListAttempts']) + 1
                                record['clientListError'] = type(error).__name__
                    while not CLIENT_STOP.is_set():
                        if not response.readline():
                            break
            except Exception as error:
                if not connected:
                    with STATE_LOCK:
                        record = STATE['records'].get(int(STATE['active_run']))
                        if record is not None:
                            record['failedSseAttempts'] = int(record['failedSseAttempts']) + 1
                            record['lastSseError'] = type(error).__name__
            finally:
                EVENT_CONNECTED.clear()
            if CLIENT_STOP.is_set():
                break
            CLIENT_STOP.wait(delay)
            delay = min(delay * 2, 30.0)

    server: subprocess.Popen | None = None
    client: threading.Thread | None = None
    try:
        server, initial_log = start_server('initial')
        initial = make_record(0, initial_log, args.recovery_timeout)
        if not wait_ready(server, initial):
            print(f'Initial Spring startup failed; inspect {initial_log}', file=sys.stderr)
            return 1
        baseline_ids = initial['serverRequestIds']
        with STATE_LOCK:
            STATE['records'] = {
                0: {
                    'clientListResponseAt': None,
                    'clientRequestIds': None,
                    'failedClientListAttempts': 0,
                    'failedSseAttempts': 0,
                    'sseReconnectSuccesses': 0,
                }
            }
        client = threading.Thread(target=client_loop, name='sse-api-client', daemon=True)
        client.start()
        if not wait_for(
            lambda: EVENT_CONNECTED.is_set() and STATE['records'][0]['clientListResponseAt'] is not None,
            30.0,
        ):
            print(f'Initial SSE/API client connection failed; inspect {initial_log}', file=sys.stderr)
            return 1
        if STATE['records'][0]['clientRequestIds'] != baseline_ids:
            print('Initial API client list did not match its baseline; stop measurement.', file=sys.stderr)
            return 1
        print(f'baseline_ready ids={baseline_ids} initial_sse=connected', flush=True)

        for run in range(1, args.cycles + 1):
            log_path = args.logs_dir / f'run-{run:02d}.log'
            record = make_record(run, log_path, args.recovery_timeout)
            with STATE_LOCK:
                STATE['active_run'] = run
                STATE['records'][run] = record
            if server.poll() is None:
                server.kill()
                server.wait(timeout=5.0)
            time.sleep(0.05)
            server, log_path = start_server(f'run-{run:02d}')
            record['serverPid'] = server.pid
            record['serverLog'] = str(log_path)
            if not wait_ready(server, record):
                record['timedOut'] = True
                record['timeoutReason'] = 'authenticated-list-readiness-timeout'
                record['serverRequestIds'] = baseline_ids
                write_record(args.output, record)
                print(f'run={run} TIMEOUT waiting for first authenticated list 200', flush=True)
                continue
            if record.get('clientRequestIds') is not None and record.get('apiListMatchedAt') is None:
                expected = set(record['serverRequestIds']) - set(record['locallyRejectedIds'])
                if set(record['clientRequestIds']) == expected:
                    record['apiListMatchedAt'] = record.get('clientListResponseAt')
            if not wait_for(lambda: record.get('apiListMatchedAt') is not None, args.recovery_timeout):
                record['timedOut'] = True
                record['timeoutReason'] = 'sse-reconnect-client-list-timeout'
            if record.get('serverRequestIds') is None:
                record['serverRequestIds'] = baseline_ids
            if record.get('clientRequestIds') is not None:
                expected = set(record['serverRequestIds']) - set(record['locallyRejectedIds'])
                received = set(record['clientRequestIds'])
                record['staleApiRequestIds'] = sorted(received - expected)
                record['missingApiRequestIds'] = sorted(expected - received)
            write_record(args.output, record)
            if record.get('serverReadyAt') and record.get('apiListMatchedAt'):
                ready = datetime.datetime.fromisoformat(str(record['serverReadyAt']).replace('Z', '+00:00'))
                matched = datetime.datetime.fromisoformat(str(record['apiListMatchedAt']).replace('Z', '+00:00'))
                delta_ms = int((matched - ready).total_seconds() * 1000)
                print(
                    f'run={run}/{args.cycles} ready_to_api_match={delta_ms}ms '
                    f'sse_failures={record["failedSseAttempts"]} '
                    f'api_residual={len(record.get("staleApiRequestIds") or [])} '
                    f'missing={len(record.get("missingApiRequestIds") or [])} timeout={record["timedOut"]}',
                    flush=True,
                )
            else:
                print(f'run={run}/{args.cycles} timeout={record["timedOut"]}', flush=True)
            time.sleep(0.05)
    finally:
        if server is not None and server.poll() is None:
            server.kill()
            server.wait(timeout=5.0)
        CLIENT_STOP.set()
        if client is not None:
            client.join(timeout=3.0)
    print(f'JSONL={args.output}', flush=True)
    print(f'Spring logs={args.logs_dir}', flush=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
