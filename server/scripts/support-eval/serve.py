#!/usr/bin/env python3
"""Launch only a NEW disposable evaluation DB; never restore the user's database."""
import argparse,datetime,importlib.util,json,os,socket,subprocess
from pathlib import Path
SERVER=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('local',SERVER/'scripts/local-support-web.py'); local=importlib.util.module_from_spec(spec);spec.loader.exec_module(local)
p=argparse.ArgumentParser();p.add_argument('--port',type=int,default=18083);p.add_argument('--model',default='gpt-6-luna');p.add_argument('--clock');a=p.parse_args()
if a.port==18082 or a.port<1024: p.error('User port forbidden')
root=SERVER/'build/support-eval';run=root/'server';run.mkdir(parents=True,exist_ok=True)
with socket.socket() as sock: sock.bind(('127.0.0.1',a.port))
clock=a.clock or datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat().replace('+00:00','Z')
(root/'clock.txt').write_text(clock)
(root/'budget.json').unlink(missing_ok=True)
for file in ['stop.request','ready.json','fixture.sql','fixture-state.json']: (run/file).unlink(missing_ok=True)
env=local.isolated_environment(os.environ);env['OPENAI_API_KEY']=local.key_for_child(local.KEY_FILE)
env['SETTY_AI_SUPPORT_DAILY_TEXT_LIMIT']='0'
env['SETTY_AI_SUPPORT_MONTHLY_TEXT_LIMIT']='0'
command=[str(SERVER/'gradlew'),'--no-daemon','runLocalSupportWeb',f'-PsettyLocal.run-directory={run}',f'-PsettyLocal.port={a.port}','-PsettyLocal.provider=openai','-PsettyLocal.ai-enabled=true',f'-PsettyLocal.model={a.model}',f'-PsettyLocal.eval-root={root}',f'-PsettyLocal.eval-clock={clock}']
raise SystemExit(subprocess.call(command,cwd=SERVER,env=env))
