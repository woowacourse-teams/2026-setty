/**
 * 배차 요청 개인정보 처리 안내의 값이다.
 * 처리 주체·문의 채널·철회·삭제·잔존 데이터 안내는 DEC-020의 견적 FE 적용안을 배차 안내에도 같은 문장으로 쓴다.
 * 수집 항목·이용 목적·보유 기간은 배차 시안 값을 그대로 두며, 실제 사용자 접수 전 팀이 확정해야 한다.
 */
export const DISPATCH_PRIVACY_POLICY = {
  controller: 'SETTY 프로젝트팀',
  /** FE 구현용 임시값이다. 실제 접수 전 수신 가능한 채널로 바꾼다. */
  contactEmail: 'setty@example.com',
  effectiveDate: '2026년 8월 12일',
  items: '이름, 연락처, 받는 주소, 거래 정보',
  /** server 계약에 있는 값과 운영자가 남기는 기록만 적는다. */
  generatedItems:
    '동의 여부·안내문 버전, 요청 상태·접수 시각, 판매자 입력 완료 여부, 운영자가 기록한 최종 금액',
  purpose: '거래 상대방과의 중개, 대금 보관·정산, 배송 및 고객 안내',
  refusalEffect:
    '동의하지 않을 수 있지만, 이름·연락처·받는 주소가 없으면 거래 상대방을 중개하고 배송을 안내할 수 없어 배차 요청이 접수되지 않아요',
  /** 시안에 적힌 값이며 팀 확정 전까지 임의로 바꾸지 않는다. */
  retentionPeriod: '거래 완료 후 5년',
  version: '2026-08-12',
} as const;

export const DISPATCH_PRIVACY_POLICY_VERSION = DISPATCH_PRIVACY_POLICY.version;
