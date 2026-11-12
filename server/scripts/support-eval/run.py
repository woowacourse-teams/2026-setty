#!/usr/bin/env python3
"""Sequential live/replay evaluation against a guarded, disposable local server."""
import argparse,datetime,fcntl,hashlib,json,os,re,subprocess,time,urllib.request,urllib.error,urllib.parse
from pathlib import Path
from judge import judge
SERVER=Path(__file__).resolve().parents[2];ROOT=SERVER/'build/support-eval';RUNS=ROOT/'runs';HERE=Path(__file__).resolve().parent

def write(path,value): path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')
def digest(value): return hashlib.sha256(json.dumps(value,sort_keys=True,ensure_ascii=False,separators=(',',':')).encode()).hexdigest()
def command(args,**kwargs): return subprocess.run(args,check=True,capture_output=True,**kwargs).stdout

def guard_url(url):
 u=urllib.parse.urlparse(url)
 if u.scheme!='http' or u.username or u.password or u.port==18082 or u.path not in ('','/') or u.hostname not in ('127.0.0.1','::1','localhost'): raise ValueError('Only loopback HTTP, never user port 18082')
 return url.rstrip('/')

class Database:
 def __init__(self,ready):
  self.cid=ready['databaseContainerId'];self.guard()
  self.tables=self.sql('SHOW TABLES').splitlines();self.tables=[t for t in self.tables if t.lower()!='flyway_schema_history']
 def guard(self):
  if not re.fullmatch('[0-9a-f]{64}',self.cid): raise ValueError('Invalid container ID')
  for p in [SERVER/'build/local-support-web-18082/ready.json',SERVER/'build/local-support-web/ready.json']:
   if p.exists() and json.loads(p.read_text()).get('databaseContainerId')==self.cid: raise ValueError('USER DATABASE FORBIDDEN')
  data=json.loads(command(['docker','inspect',self.cid]))[0]
  if not data['Config'].get('Labels',{}).get('org.testcontainers'): raise ValueError('Not a Testcontainers DB')
  if not any(e=='MYSQL_DATABASE=setty_ai_eval' for e in data['Config']['Env']): raise ValueError('Unexpected database')
 def sql(self,sql):
  return command(['docker','exec','-i',self.cid,'mysql','--user=setty_local_eval','--password=synthetic-local-database-only','--default-character-set=utf8mb4','--batch','--raw','--skip-column-names','setty_ai_eval'],input=sql.encode()).decode().strip()
 def rows(self,table,where='1=1'):
  columns=[line.split('\t')[0] for line in self.sql('SHOW COLUMNS FROM `'+table+'`').splitlines()]
  fields=','.join("'"+c+"',`"+c+'`' for c in columns)
  raw=self.sql('SELECT JSON_OBJECT('+fields+') FROM `'+table+'` WHERE '+where)
  return [json.loads(line) for line in raw.splitlines() if line]
 def business(self):
  raw=self.sql("SELECT JSON_OBJECT('id',o.id,'order_status',o.order_status,'delivery_status',o.delivery_status,'actual_delivery_status',d.status,'sale_status',l.sale_status) FROM orders o LEFT JOIN delivery d ON d.order_id=o.id JOIN listings l ON l.id=o.listing_id ORDER BY o.id")
  return [json.loads(line) for line in raw.splitlines()]
 def drain(self):
  start=time.monotonic();quiet=0
  while time.monotonic()-start<30:
   count=int(self.sql("SELECT (SELECT COUNT(*) FROM ai_support_turn WHERE status='PROCESSING')+(SELECT COUNT(*) FROM ai_support_cancellation WHERE status='ACCEPTED')+(SELECT COUNT(*) FROM EVENT_PUBLICATION WHERE COMPLETION_DATE IS NULL)"))
   quiet=quiet+1 if count==0 else 0
   if quiet>=2: return
   time.sleep(.15)
  raise RuntimeError('Async work did not settle; refusing fixture restoration')
 def baseline(self,path):
  self.guard();self.drain()
  canonical=ROOT/'server/fixture.sql'; state=ROOT/'server/fixture-state.json'
  if canonical.exists():
   self.dump=canonical.read_bytes();self.expected=json.loads(state.read_text());self.expected_hash=digest(self.expected)
   self.reset();path.write_bytes(self.dump);os.chmod(path,0o600);return
  if self.rows('ai_support_conversation') or self.rows('ai_support_cancellation'):
   raise RuntimeError('No pristine baseline: restart isolated eval server or restore its initial snapshot')
  self.sql("UPDATE orders SET order_status='CANCELLED' WHERE id=930026; UPDATE delivery SET status='CANCELLED' WHERE order_id=930026;")
  self.expected=self.business();self.expected_hash=digest(self.expected)
  raw=command(['docker','exec',self.cid,'mysqldump','--user=setty_local_eval','--password=synthetic-local-database-only','--no-create-info','--skip-comments','--skip-add-locks','--skip-disable-keys','--skip-extended-insert','--complete-insert','--hex-blob','--no-tablespaces','setty_ai_eval']+self.tables)
  path.write_bytes(raw);os.chmod(path,0o600);self.dump=raw
  canonical.write_bytes(raw);os.chmod(canonical,0o600);write(state,self.expected)
 def reset(self):
  start=time.monotonic();self.guard();self.drain()
  sql='SET FOREIGN_KEY_CHECKS=0;'+''.join('TRUNCATE TABLE `'+t+'`;' for t in self.tables)+'SET FOREIGN_KEY_CHECKS=1;'
  self.sql(sql+self.dump.decode())
  if digest(self.business())!=self.expected_hash: raise RuntimeError('Restored order/delivery state hash differs')
  if self.rows('ai_support_cancellation', 'member_id=920001'): raise RuntimeError('Buyer cancellation history was not cleared')
  return time.monotonic()-start
 def snapshot(self,conversation):
  conv=self.rows('ai_support_conversation',f'id={conversation}')
  q=conv[0].get('active_question_json') if conv else None
  if isinstance(q,str): q=json.loads(q)
  return dict(question=q,cancellations=self.rows('ai_support_cancellation','member_id=920001'),business=self.business())

def api(base,path,payload):
 req=urllib.request.Request(base+path,data=json.dumps(payload).encode(),headers={'Content-Type':'application/json','Authorization':'Bearer support-eval-buyer-token'},method='POST')
 try:
  with urllib.request.urlopen(req,timeout=75) as r: return r.status,json.load(r)
 except urllib.error.HTTPError as error: return error.code,json.loads(error.read())

def holdout_check(cases):
 files=list((SERVER/'src/main/resources/aisupport').glob('*.md'))
 if not files: raise ValueError('Missing planner/policy files')
 text='\n'.join(f.read_text() for f in files)
 # Shared setup phrases are dev; only variant-specific holdout expressions are held out.
 dev={s['text'] for c in cases if c['split']=='dev' for s in c['steps'] if 'text' in s}
 bad=sorted({s['text'] for c in cases if c['split']=='holdout' for s in c['steps'] if 'text' in s and s['text'] not in dev and s['text'] in text})
 if bad: raise ValueError('Holdout contamination: '+repr(bad))
 return {str(f.relative_to(SERVER)):hashlib.sha256(f.read_bytes()).hexdigest() for f in files}

def select_cases(cases,case_ids=None,variants=None,smoke=False):
 def parse(value):
  if not value: return None
  result=[item.strip() for item in value.split(',')]
  if any(not item for item in result): raise ValueError('Empty selection element')
  return set(result)
 requested_ids=parse(case_ids);requested_variants=parse(variants)
 all_ids={case['id'] for case in cases};all_keys={case['key'] for case in cases}
 if requested_ids and requested_ids-all_ids: raise ValueError('Unknown case IDs: '+str(sorted(requested_ids-all_ids)))
 if requested_variants and requested_variants-all_keys: raise ValueError('Unknown variant keys: '+str(sorted(requested_variants-all_keys)))
 selected=[case for case in cases if (not requested_ids or case['id'] in requested_ids)
           and (not requested_variants or case['key'] in requested_variants) and (not smoke or case['smoke'])]
 if not selected: raise ValueError('No selected cases')
 companions={case['compare_with'] for case in selected if case.get('compare_with')}
 missing=companions-all_keys
 if missing: raise ValueError('Missing required comparison variants: '+str(sorted(missing)))
 selected_keys={case['key'] for case in selected}|companions
 return [case for case in cases if case['key'] in selected_keys]

def conditional_decision(step,turns):
 condition=step.get('when')
 if not condition: return 'OCCURRED'
 if not turns or turns[-1].get('http')!=200 or turns[-1].get('response',{}).get('status')!='SUCCEEDED': return 'UNKNOWN'
 action=turns[-1].get('plan',{}).get('action')
 if not action: return 'UNKNOWN'
 return 'OCCURRED' if action==condition['previous_plan'] else 'NOT_OCCURRED'

def compare_empty_searches(left,right):
 def features(dialogue):
  if not dialogue or not dialogue.get('turns'): return None
  turn=dialogue['turns'][-1];response=turn.get('response',{});cards=response.get('cards',[])
  pages=[card for card in cards if card.get('type')=='ORDERS']
  query=(turn.get('input') or {}).get('text') or ''
  number=re.search(r'(?<!\d)\d{6}(?!\d)',query)
  answer=response.get('answer') or ''
  normalized=re.sub(r'(?<!\d)'+number.group(0)+r'(?!\d)','<ORDER_ID>',answer) if number else answer
  return dict(success=turn.get('http')==200 and response.get('status')=='SUCCEEDED',
              types=[c.get('type') for c in cards],pages=pages,
              purpose=[c.get('purpose') for c in pages],answer=normalized,
              not_found=bool(re.search(r'없|못|비어\s*있',answer)))
 a,b=features(left),features(right)
 if a is None or b is None: return ['missing_companion']
 problems=[]
 if not a['success'] or not b['success']: problems.append('turn_success')
 if not a['pages'] or not b['pages']: problems.append('missing_orders_card')
 if a['types']!=b['types']: problems.append('card_shape')
 if any(page.get('orders')!=[] for page in a['pages']+b['pages']): problems.append('orders_not_empty')
 if a['purpose']!=b['purpose']: problems.append('purpose')
 if not a['not_found'] or not b['not_found'] or a['answer']!=b['answer']: problems.append('answer_kind')
 return problems

def normalize(value):
 volatile={'turnId','clientRequestId','sourceTurnId','question_turn_id','consent_turn_id','request_id','requestId','createdAt','processingDeadlineAt','acceptedAt','accepted_at','decided_at','observedAt','confirmationExpiresAt','expiresAt','duration_ms','durationMs','completed_at','created_at'}
 if isinstance(value,dict): return {k:normalize(v) for k,v in value.items() if k not in volatile}
 if isinstance(value,list): return [normalize(v) for v in value]
 return value

def comparable(turn):
 return normalize({k:turn[k] for k in ['response','question','cancellations','business','audit','http']})

def execute(args):
 base=guard_url(args.url);spec=json.loads((HERE/'cases.json').read_text());hashes=holdout_check(spec['cases'])
 variants=getattr(args,'variants',None)
 selected=select_cases(spec['cases'],args.cases,variants,args.smoke)
 source=RUNS/args.replay if args.replay else None
 if source:
  original=json.loads((source/'run.json').read_text());keys=set(original['selected'])
  if variants and any(c['key'] not in keys for c in selected): raise ValueError('Selected replay variant or its comparison partner is missing from recording')
  selected=[c for c in selected if c['key'] in keys];args.repeat=original['repeat']
  if not selected: raise ValueError('No selected variants exist in the recording')
  if (ROOT/'clock.txt').read_text()!=original['clock']: raise ValueError('Replay must use original --clock')
 ready=json.loads((ROOT/'server/ready.json').read_text())
 if guard_url(ready['baseUrl'])!=base: raise ValueError('Target does not match dedicated eval server')
 db=Database(ready)
 run_id=args.run_id or datetime.datetime.now().strftime('%Y%m%d-%H%M%S')+('-replay' if source else '-smoke' if args.smoke else '-full')
 directory=RUNS/run_id;directory.mkdir(parents=True,exist_ok=False)
 if source: (ROOT/'replay-checks.jsonl').unlink(missing_ok=True)
 meta=dict(id=run_id,started=datetime.datetime.now().isoformat(),commit=command(['git','rev-parse','HEAD'],cwd=SERVER).decode().strip(),dirty=bool(command(['git','status','--porcelain','--untracked-files=no'],cwd=SERVER)),model='gpt-6-luna',provider='replay' if source else ready['provider'],prompt='cancel-v10.9-list-ordinal',rule='final-consent-v2',case_version=spec['version'],case_hash=hashlib.sha256((HERE/'cases.json').read_bytes()).hexdigest(),fixture_hash=hashlib.sha256((SERVER/'src/test/resources/aisupport/eval-fixture.sql').read_bytes()).hexdigest(),prompt_hashes=hashes,clock=(ROOT/'clock.txt').read_text(),repeat=args.repeat,selected=[c['key'] for c in selected],mode='replay' if source else 'smoke' if args.smoke else 'full',source=args.replay,dialogues=[],reset_seconds=0,stop_reason=None)
 meta['budget_start']=json.loads((ROOT/'budget.json').read_text()).get('estimateUsd',0) if (ROOT/'budget.json').exists() else 0
 write(directory/'run.json',meta);start=time.monotonic()
 try:
  db.baseline(directory/'baseline.sql');meta['fixture_state_hash']=db.expected_hash
  if source:
   db.dump=(source/'baseline.sql').read_bytes();(directory/'baseline.sql').write_bytes(db.dump)
  # Fixed virtual application date and SQL fixture date must agree. No midnight drift during a run.
  meta['fixture_date']=db.sql("SELECT DATE(CONVERT_TZ(UTC_TIMESTAMP(),'+00:00','+09:00'))")
  for repeat in range(1,args.repeat+1):
   for case in selected:
    if not source and (ROOT/'budget.json').exists():
     budget=json.loads((ROOT/'budget.json').read_text())
     if budget['estimateUsd']+.02>1 or budget['consecutiveErrors']>=3: raise RuntimeError('Cost/provider circuit open')
    # If midnight passes, stop instead of running mismatched relative-date cases.
    if not source and datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=9))).date().isoformat()!=meta['fixture_date']: raise RuntimeError('KST date changed: restart with a fresh dated fixture')
    reset=db.reset();meta['reset_seconds']+=reset
    if case.get('setup')=='historical_930026':
     db.sql("INSERT INTO ai_support_cancellation(request_id,member_id,order_id,question_turn_id,consent_turn_id,consent_method,policy_version,status,accepted_at,decided_at,reason_code) VALUES('historical-930026',920001,930026,900001,900002,'BUTTON','cancellation-v1','COMPLETED','2026-11-01 00:00:00','2026-11-01 00:00:01',NULL)")
    code,conv=api(base,'/api/support/conversations',{})
    if code!=201: raise RuntimeError('Cannot create isolated conversation: '+str(code))
    cid=conv['id'];prev=db.snapshot(cid);prev['response']={};dialogue=dict(key=case['key'],id=case['id'],split=case['split'],category=case['category'],repeat=repeat,reset_seconds=reset,fixture_state_hash=db.expected_hash,turns=[],failures=[],guards=[],conditional_turns=[])
    for index,step in enumerate(case['steps']):
     if step.get('when'):
      decision=conditional_decision(step,dialogue['turns'])
      dialogue['conditional_turns'].append(dict(turn=index+1,status=decision,condition=step['when']))
      if decision=='UNKNOWN':
       dialogue['failures'].append(f't{index+1}:missing_conditional_plan');continue
      if decision=='NOT_OCCURRED': continue
     if step.get('sql')=='accept_delivery_930013': db.sql("UPDATE delivery SET status='ACCEPTED' WHERE order_id=930013")
     key=f"{case['key']}-r{repeat}-t{index+1}"
     tape=source/'tape' if source else directory/'tape';write(ROOT/'control.json',dict(key=key,mode='replay' if source else 'record',tape=str(tape)))
     inp=dict(type='TEXT',text=step['text']) if 'text' in step else None
     if inp is None:
      ref=step.get('ref',-1);prior=dialogue['turns'][ref] if dialogue['turns'] else None
      if prior is None or 'turnId' not in prior['response']:
       turn=dict(index=index,input=step,response=dict(status='SETUP_FAILED',cards=[]),http=0,audit=[],tokens=[0,0],duration_ms=0,calls=0,**db.snapshot(cid));turn['new_cancellations']=[];turn['plan']={};turn['wrong_cancellations']=0
       turn['failures']=['missing_button_source'];turn['guards']=[];dialogue['turns'].append(turn);dialogue['failures'].append(f't{index+1}:missing_button_source');prev=turn;continue
      pcs=prior['response'].get('cards',[])
      oid=next((c['order']['orderId'] for c in pcs if c.get('type')=='ELIGIBILITY'),None)
      if oid is None: oid=next((o['orderId'] for c in pcs if c.get('type')=='ORDERS' for o in c.get('orders',[])),None)
      if step['button']!='NEXT_ORDERS' and oid is None:
       # Send no fabricated target; record a failed preparation with no application request.
       turn=dict(index=index,input=step,response=dict(status='SETUP_FAILED',cards=[]),http=0,audit=[],tokens=[0,0],duration_ms=0,calls=0,**db.snapshot(cid));turn['new_cancellations']=[];turn['plan']={};turn['wrong_cancellations']=0;turn['failures']=['missing_button_card'];turn['guards']=[];dialogue['turns'].append(turn);dialogue['failures'].append(f't{index+1}:missing_button_card');prev=turn;continue
      inp=dict(type=step['button'],sourceTurnId=prior['response']['turnId'])
      if step['button']!='NEXT_ORDERS': inp['orderId']=oid
     code,response=api(base,f'/api/support/conversations/{cid}/messages',dict(clientRequestId=key,input=inp))
     db.drain();snap=db.snapshot(cid)
     stored=db.rows('ai_support_turn',f"conversation_id={cid} AND client_request_id='{key}'")
     row=stored[0] if stored else {};audit=row.get('tool_summary_json') or []
     if isinstance(audit,str): audit=json.loads(audit)
     turn=dict(index=index,expected=step['expect'],input=inp,response=response,http=code,audit=audit,tokens=[row.get('input_tokens',0),row.get('output_tokens',0)],duration_ms=row.get('duration_ms') or 0,calls=len(list(tape.glob(key+'-*.json'))),**snap)
     oldids={r['request_id'] for r in prev['cancellations']};turn['new_cancellations']=[r for r in snap['cancellations'] if r['request_id'] not in oldids]
     failures,guards=judge(case,step,turn,prev,dialogue['turns']);turn['failures']=failures;turn['guards']=guards;turn['human']=case['human'].get(str(index))
     if source:
      old_dialogue=next(d for d in original['dialogues'] if d['key']==case['key'] and d['repeat']==repeat)
      old=next((t for t in old_dialogue['turns'] if t['index']==index),None)
      turn['replay_equal']=old is not None and comparable(turn)==comparable(old)
     dialogue['turns'].append(turn);dialogue['failures'] += [f't{index+1}:{f}' for f in failures];dialogue['guards'] += guards;prev=turn
    if case.get('compare_with'):
     companion=next((d for d in meta['dialogues'] if d['key']==case['compare_with'] and d['repeat']==repeat),None)
     differences=compare_empty_searches(companion,dialogue)
     dialogue['cross_case_comparison']=dict(companion=case['compare_with'],passed=not differences,differences=differences)
     dialogue['failures'] += ['comparison:'+difference for difference in differences]
    if source and case.get('id')=='B14':
     old_dialogue=next(d for d in original['dialogues'] if d['key']==case['key'] and d['repeat']==repeat)
     dialogue['replay_conditional_equal']=dialogue['conditional_turns']==old_dialogue.get('conditional_turns',[])
    dialogue['passed']=not dialogue['failures'] and not dialogue['guards'];meta['dialogues'].append(dialogue);meta['wall_seconds']=time.monotonic()-start
    write(directory/'run.json',meta);write(directory/f"{case['key']}-r{repeat}.json",dialogue)
    print(f"{run_id} {len(meta['dialogues'])}/{len(selected)*args.repeat} {case['key']} r{repeat} {'PASS' if dialogue['passed'] else 'FAIL'} {','.join(dialogue['failures'])} G={dialogue['guards']}",flush=True)
 except Exception as error:
  meta['stop_reason']=str(error);print('STOP:',str(error),flush=True)
 finally:
  meta['wall_seconds']=time.monotonic()-start
  if source and (ROOT/'replay-checks.jsonl').exists(): meta['replay_hash_checks']=[json.loads(l) for l in (ROOT/'replay-checks.jsonl').read_text().splitlines()]
  meta['budget_estimate_usd']=0 if source else (json.loads((ROOT/'budget.json').read_text()).get('estimateUsd',0)-meta['budget_start'] if (ROOT/'budget.json').exists() else 0)
  write(directory/'run.json',meta)
  from summarize import summarize
  summarize(directory)
 return 2 if meta['stop_reason'] else 0

if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--url',default='http://127.0.0.1:18083');p.add_argument('--smoke',action='store_true');p.add_argument('--cases');p.add_argument('--variants');p.add_argument('--repeat',type=int,default=1);p.add_argument('--run-id');p.add_argument('--replay');a=p.parse_args()
 if a.repeat<1: p.error('repeat must be positive')
 ROOT.mkdir(parents=True,exist_ok=True)
 with (ROOT/'runner.lock').open('w') as lock:
  try: fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
  except BlockingIOError: p.error('Another evaluation is running; concurrent execution is forbidden')
  raise SystemExit(execute(a))
