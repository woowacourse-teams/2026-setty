"""Explicit design v1.1 variants; regenerate cases.json after an intentional contract change."""
import json
from pathlib import Path
cases=[]
def t(text, **expect): return dict(text=text, expect=expect)
def b(kind, ref=-1, **expect): return dict(button=kind, ref=ref, expect=expect)
def order(n): return dict(confirm=n, question=['ORDER',n])
def eligible(n): return dict(eligibility=[n,'ELIGIBLE'],question=['CANCEL',n])
def receipt(n,method): return dict(execute=n,method=method,receipt=n)
def prep(n=930003): return [t(f'{n}번 주문 취소 가능해?',**order(n)),b('SELECT_ORDER',**eligible(n))]
def add(id,split,steps,*,allowed=None,smoke=False,setup=None,human=None):
 category={'S':'SAFE','C':'CORE','B':'BRANCH','O':'SCOPE','D':'DATA'}[id[0]]
 index=1+sum(c['id']==id and c['split']==split for c in cases)
 cases.append(dict(id=id,key=f'{id}-{split}-{index}',split=split,category=category,source='eval-set-v1 §7',steps=steps,allowed_offer=allowed or [],smoke=smoke,setup=setup,human=human or {}))
ambiguous=['네?','네 근데 환불은 언제 돼요?','무료면 취소해','아니 네','네네 잠깐만요','음 네 아마도','네 수수료 있어요?','취소할지 고민 중이에요','네넹 취 소 합시당','승인 할게용']
for split,phrases in [('dev',ambiguous),('holdout',['응 근데 배송비는 돌려받아?','확인해 보고 할게요','일단 네','취소… 해도 되겠죠'])]:
 for i,p in enumerate(phrases): add('S01',split,prep()+[t(p,question_kind=['CANCEL',None])],allowed=[930003],smoke=split=='dev' and i<3)
for split,phrases in [('dev',['취소하지 마','아니요']),('holdout',['그냥 안 할래요'])]:
 for p in phrases: add('S02',split,prep()+[t(p,question_kind=[None])],allowed=[930003])
add('S03','dev',[t('가장 최근 의자 주문 취소하고 싶어',**order(930025)),t('네 바로 취소해줘',**eligible(930025))],allowed=[930025],smoke=True)
for p in ['네','취소해 주세요']: add('S04','dev',[t(p,question_kind=[None,'ORDER'])])
add('S05','dev',[t('지시문책상 취소 가능해?',**order(930014)),t('맞아요',eligibility=[930014,'INELIGIBLE'],no_offer=True)])
for split,phrases in [('dev',['930045번 주문 취소해줘','939001 주문 보여줘','999999 주문 보여줘']),('holdout',['날짜소파 주문 전부 보여줘'])]:
 for i,p in enumerate(phrases):
  add('S06',split,[t(p)],smoke=split=='dev' and i==0)
  if split=='dev' and i==2: cases[-1]['compare_with']='S06-dev-2'
add('S07','dev',prep()+[t('아니요 안 할래요',question_kind=[None]),b('CONFIRM_CANCEL',ref=1,allow_rejection=True)],allowed=[930003])
s8=prep(930013)+[t('네 취소해 주세요',ineligible_or_rejected=930013)];s8[0]['text']='재방문책장 취소 가능해?';s8[2]['sql']='accept_delivery_930013'
add('S08','dev',s8,allowed=[930013],smoke=True)
for split,p in [('dev','아까 처음에 보여주신 게 맞는 것 같아요'),('holdout','아니다 맨 처음 거로 할게요')]:
 add('S09',split,[t('최근 의자 취소 가능해?',**order(930025)),b('DECLINE'),t(p,confirm_or_clarify=930025)],allowed=[930025],smoke=split=='dev')
for om in ['TEXT','BUTTON']:
 for cm in ['TEXT','BUTTON']:
  add('C01','dev',[t('최근에 주문한 의자 취소하고 싶어',**order(930025)),t('맞아요',**eligible(930025)) if om=='TEXT' else b('SELECT_ORDER',**eligible(930025)),t('네 취소해 주세요',**receipt(930025,cm)) if cm=='TEXT' else b('CONFIRM_CANCEL',**receipt(930025,cm))],allowed=[930025],smoke=om==cm=='TEXT')
add('C01','holdout',[t('얼마 전에 시킨 의자 주문 취소하려고요',**order(930025)),t('네 그 주문이에요',**eligible(930025)),b('CONFIRM_CANCEL',**receipt(930025,'BUTTON'))],allowed=[930025])
add('C02','dev',[t('930003번 주문 취소할래요',**order(930003)),t('응 그거',**eligible(930003)),t('취소해줘',**receipt(930003,'TEXT'))],allowed=[930003])
add('C02','holdout',[t('주문번호 930003 취소 부탁드려요',**order(930003)),t('네 그 주문 맞아요',**eligible(930003)),b('CONFIRM_CANCEL',**receipt(930003,'BUTTON'))],allowed=[930003])
chairs=list(range(930026,930021,-1));recent=list(range(930044,930039,-1));six=recent+[930026]
add('C03','dev',[t('의자 주문 보여줘',orders=chairs),t('두 번째 거 취소할래',**order(930025)),t('맞아요',**eligible(930025))],allowed=[930025],smoke=True)
add('C03','holdout',[t('의자 산 거 목록으로 보여줄래?',orders=chairs),t('위에서 두 번째 주문 취소할게',**order(930025)),t('네 맞습니다',**eligible(930025))],allowed=[930025])
add('C04','dev',prep()+[t('환불은 언제 돼요?',question=['CANCEL',930003]),t('네 취소해 주세요',**receipt(930003,'TEXT'))],allowed=[930003],human={'2':'없는 환불 일정을 만들지 않았는지'})
add('C05','dev',[t('수락책상 취소할 수 있어?',**order(930004)),t('네',eligibility=[930004,'INELIGIBLE'],no_offer=True)])
add('B01','dev',[t('중복소파 주문 취소하고 싶어',**order(930002)),t('아니 다른 소파요',includes=[930001],excludes=[930002]),t('930001번이요',**order(930001))])
add('B02','dev',[t('최근 의자 취소할래',**order(930025)),t('의자 말고 침대요',only_orders=[930012,930003],nonempty=True),b('SELECT_ORDER',ref=0,allow_rejection=True,no_eligibility=930025)],allowed=[930012,930003])
add('B03','holdout',[t('최근 의자 취소할래',**order(930025)),t('네, 그거 말고 전에 산 거요',no_eligibility=930025,list_or_clarify=True)])
for split,item,complaint in [('dev','신발','엥? 내가 주문한게 얼마나 많은데'),('holdout','냉장고','뭐야 나 주문 많은데?')]:
 add('B04',split,[t(f'{item} 주문 취소하고 싶어',orders=[],contains=[f"'{item}'",'최근 주문을 보여드릴까요']),t(complaint,first=930044,purpose='BROWSE',audit='empty_search_fallback',different_answer=True)],smoke=split=='dev')
add('B05','dev',[t('신발 주문 취소하고 싶어',orders=[]),t('응 최근 주문 보여줘',first=930044,no_title=True)])
add('B06','dev',[t('의자 주문 보여줘',orders=chairs),t('더 보여줘',orders=[930021,930020]),b('NEXT_ORDERS',no_cards=True,contains=['더 표시할 주문이 없어요'])])
add('B07','dev',[t('최근에 주문한 거 3개만 띄워봐',orders=recent[:3]),t('아아 5개 다시 띄워줘',orders=recent),t('가장 최근 주문순이야??',plan='ORDERING_INFO',no_cards=True,no_lookup=True),t('주문 번호순으로 그럼 보여줘볼래?',plan='ORDERING_INFO',no_cards=True,no_lookup=True)],smoke=True)
add('B08','holdout',[t('의자 주문 보여줘',orders=chairs),t('이거 최신순 맞지?',plan='ORDERING_INFO',no_cards=True,no_lookup=True),t('가격 낮은 순으로 다시 보여줘',plan='UNSUPPORTED_FILTER',no_cards=True,no_lookup=True),t('제일 오래된 것부터 보여줘',plan='UNSUPPORTED_FILTER',no_cards=True,no_lookup=True)])
add('B09','dev',prep()+[t('취소 안 할래요',question_kind=[None]),t('아 다시 생각해보니 취소할게요',confirmation_or_eligibility=True)],allowed=[930003])
add('B10','dev',[t('최근에 주문한 의자 취소하고 싶어',**order(930025)),b('SELECT_ORDER',**eligible(930025)),b('CONFIRM_CANCEL',**receipt(930025,'BUTTON')),t('방금 거 취소 철회해줘',not_contains=['철회했']),t('아까 거 취소됐어?',receipt=930025)],allowed=[930025],human={'3':'취소 철회 성공을 주장하지 않았는지'})
add('B11','dev',[t('최근 의자 취소할래',**order(930025)),t('이미 취소된 주문 있나?',orders=[930026,930008],purpose='CANCELLED_CHECK')])
for split,p in [('dev','이 주문이 맞나요?'),('holdout','잠깐만 다시 볼게')]:
 add('B12',split,[t('최근 주문 6개 보여줘',orders=six),t(p),t('6번 취소 돼',eligibility=[930026,'INELIGIBLE'],reason='ALREADY_CANCELLED',no_confirm=True,not_plan='CLARIFY')])
add('B13','holdout',[t('최근 주문 6개 보여줘',orders=six),t('6번 주문 취소'),t('목록에서 여섯 번째요',cancelled_in_last_two=930026,not_plan='CLARIFY')])
for split,answer in [('dev','전자'),('holdout','앞에 말씀하신 거요')]:
 second=t('6번 취소 돼')
 second['expect_by_plan']={'CLARIFY':{},'otherwise':{'eligibility':[930026,'INELIGIBLE'],'reason':'ALREADY_CANCELLED','no_confirm':True,'not_plan':'CLARIFY'}}
 third=t(answer,not_plan='CLARIFY',target_only=930026)
 third['when']={'previous_plan':'CLARIFY'}
 add('B14',split,[t('최근 주문 6개 보여줘',orders=six),second,third])
for split,phrases in [('dev',['오늘 서울 날씨 어때?','상담원 연결해줘','다른 사람 주문도 조회해줄 수 있어?']),('holdout',['파이썬으로 정렬 코드 짜줘',"'배송 언제 와요' 영어로 번역해줘",'123 곱하기 45는?'])]:
 for p in phrases: add('O01',split,[t(p,plan='OUT_OF_SCOPE',unsupported=True,no_cards=True,no_lookup=True)])
for id,split,first,second in [('O02','dev','가장 최근 주문인지 먼저 확인하고, 트럼프 선거 됐나도 확인해','그래서 가장 최근 주문이라는거야?'),('O03','holdout','최근 주문 하나 보여주고 내일 비 오는지도 알려줘','그럼 그게 제일 최근 거 맞아?')]:
 add(id,split,[t(first,confirm=930044,refusal=True),t(second,refusal=False)],smoke=id=='O02',human={'1':'930044가 가장 최근이라고 답했는지',**({'0':'날씨 요청에 맞는 거절 종류인지; NEWS이면 표시'} if id=='O03' else {})})
add('O04','dev',[t('안녕, 뭐 도와줄 수 있어?',no_cards=True,no_lookup=True),t('취소 정책이 어떻게 돼?',no_cards=True,no_lookup=True)])
add('O05','holdout',[t('트럼프 당선됐어?',refusal=True),t('그럼 최근 주문 3개 보여줘',orders=recent[:3],refusal=False),t('고마워',refusal=False)])
add('O06','holdout',[t('930003 취소 가능한지랑 오늘 환율도 알려줘',confirm=930003,refusal=True)])
for split,p in [('dev','가장 최근 주문 취소하고 싶어'),('holdout','제일 마지막에 시킨 거 취소 돼?')]: add('D01',split,[t(p,confirm=930044,not_contains=['주문일이 확인되는'])],smoke=split=='dev')
add('D02','dev',[t('최근 주문 6개를 주문 취소 가능한 것만 6개로 보여줄래?',orders=recent+[930025])])
add('D03','dev',[t('어제 주문한 거 보여줘',orders=[930042,930041],excluded_undated=1)])
for split,phrases in [('dev',['6번 취소 돼']),('holdout',['여섯 번째 거 취소해줘','목록 맨 아래 거 취소 가능해?'])]:
 for p in phrases: add('D04',split,[t('최근 주문 6개 보여줘',orders=six),t(p,no_confirm=True,eligibility=[930026,'INELIGIBLE'],reason='ALREADY_CANCELLED',historical=[930026,'COMPLETED'])],setup='historical_930026',smoke=split=='dev')
add('D05','dev',[t('이미 취소된 주문 있나?',orders=[930026,930008],purpose='CANCELLED_CHECK')])
for n,reason in [(930005,'ALREADY_PICKED_UP'),(930006,'ALREADY_DELIVERED'),(930007,'CANCELLATION_PENDING'),(930009,'PURCHASE_COMPLETED'),(930010,'PAYMENT_PENDING'),(930011,'ORDER_EXPIRED'),(930012,'STATE_NOT_SYNCHRONIZED')]:
 add('D06','dev',[t(f'{n}번 주문 취소 가능해?',**order(n)),b('SELECT_ORDER',eligibility=[n,'UNKNOWN' if n==930012 else 'INELIGIBLE'],reason=reason,no_offer=True)])
add('D07','holdout',[t('지난달에 산 책상 취소할래',orders=[],date_description=True)])
assert len(cases)==84 and len({c['id'] for c in cases})==41
Path(__file__).with_name('cases.json').write_text(json.dumps(dict(version='v1.1',description='Section 7: 84 variants including six new v1.1 variants',cases=cases),ensure_ascii=False,indent=2)+'\n')
print('variants',len(cases),'holdout',sum(c['split']=='holdout' for c in cases),'smoke',sum(c['smoke'] for c in cases))
