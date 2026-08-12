/**
 * PR #20에서 정리한 개인정보 처리 안내 값이다.
 * flow 경계 때문에 `flows/estimate`를 import하지 않고 같은 값을 여기에 둔다.
 * 값과 문장은 PR #20 원문을 그대로 쓰며 여기에서 새로 만들거나 바꾸지 않는다.
 */
export const DISPATCH_PRIVACY_POLICY = {
  controller: 'SETTY 프로젝트팀',
  contactEmail: 'setty@example.com',
  effectiveDate: '2026년 8월 6일',
  generatedItems:
    '동의 여부·안내문 버전·동의 시각, 요청 ID·상태·접수·안내 시각, 운송 가능 여부·예상 금액·실제 발송 문자 내용',
  items: '이름, 연락처, 거래 지역, 물품 종류, 50만 원 초과 여부',
  purpose: '예상 견적 요청 접수, 운송 가능 여부·예상 금액 검토, 운영자의 문자 안내',
  retentionPeriod: '견적 안내 완료 시점부터 30일',
  version: '2026-08-06',
} as const;

export const DISPATCH_PRIVACY_POLICY_VERSION = DISPATCH_PRIVACY_POLICY.version;
