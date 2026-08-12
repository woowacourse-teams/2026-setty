import { DISPATCH_PRIVACY_POLICY } from './dispatchPrivacyPolicy';

interface PrivacyConsentSection {
  label: string;
  text: string;
  /** 시안에서 굵게 강조된 항목 */
  emphasis?: boolean;
}

/**
 * 시안 `개인정보 수집·이용 동의` 화면의 문구다.
 * 시안 레이아웃을 그대로 두고, 처리 안내에 필요한 항목을 같은 형식의 절로 이어 붙인다.
 * 값은 `dispatchPrivacyPolicy.ts` 한곳에서 가져오며 여기에서 새 정책을 만들지 않는다.
 */
export const DISPATCH_PRIVACY_CONSENT_NOTICE = {
  title: ['안전한 거래 중개를 위해', '아래 정보를 수집해요'],
  sections: [
    {
      label: '처리 주체',
      text: `${DISPATCH_PRIVACY_POLICY.controller}이 배차 요청을 처리하기 위해 필요한 개인정보만 수집·이용해요`,
    },
    {
      label: '수집 항목',
      text: DISPATCH_PRIVACY_POLICY.items,
      emphasis: true,
    },
    {
      label: '요청 처리 중 생성·저장되는 정보',
      text: DISPATCH_PRIVACY_POLICY.generatedItems,
    },
    {
      label: '이용 목적',
      text: DISPATCH_PRIVACY_POLICY.purpose,
    },
    {
      label: '보유·이용 기간',
      text: `${DISPATCH_PRIVACY_POLICY.retentionPeriod}까지 보관하며, 관련 법령에 따라 안전하게 파기해요. 동의 철회나 삭제 요청이 먼저 처리되면 그 시점에 삭제해요`,
    },
    {
      label: '동의를 거부하면',
      text: DISPATCH_PRIVACY_POLICY.refusalEffect,
    },
    {
      label: '철회·삭제 요청과 파기',
      text: `동의 철회나 삭제는 ${DISPATCH_PRIVACY_POLICY.contactEmail}로 요청할 수 있어요. 운영팀이 확인해 수동으로 처리하고 처리 결과를 기록해요. 요청에 연결된 운영 기록을 포함한 전자적 파일은 복구할 수 없는 방법으로 삭제하며, 삭제 후에는 특정 개인을 알아볼 수 없는 통계만 남을 수 있어요`,
    },
    {
      label: '문의',
      text: DISPATCH_PRIVACY_POLICY.contactEmail,
    },
    {
      label: '안내문 정보',
      text: `적용일 ${DISPATCH_PRIVACY_POLICY.effectiveDate}, 버전 ${DISPATCH_PRIVACY_POLICY.version}`,
    },
  ] satisfies PrivacyConsentSection[],
} as const;
