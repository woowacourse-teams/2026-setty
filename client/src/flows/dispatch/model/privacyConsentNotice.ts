import { DISPATCH_PRIVACY_POLICY } from './dispatchPrivacyPolicy';

interface PrivacyConsentSection {
  label: string;
  text: string;
  /** 시안에서 굵게 강조된 항목 */
  emphasis?: boolean;
}

/**
 * 시안 `개인정보 수집·이용 동의` 화면의 문구다.
 * 시안 레이아웃을 그대로 두고, PR #20의 개인정보 처리 안내 전문을 같은 형식의 절로 옮긴다.
 * 문장과 값은 PR #20 원문을 그대로 쓰며 여기에서 새로 만들지 않는다.
 */
export const DISPATCH_PRIVACY_CONSENT_NOTICE = {
  title: ['안전한 거래 중개를 위해', '아래 정보를 수집해요'],
  sections: [
    {
      label: '처리 목적',
      text: DISPATCH_PRIVACY_POLICY.purpose,
    },
    {
      label: '사용자 입력 항목',
      text: DISPATCH_PRIVACY_POLICY.items,
      emphasis: true,
    },
    {
      label: '요청 처리 중 생성·저장되는 정보',
      text: DISPATCH_PRIVACY_POLICY.generatedItems,
    },
    {
      label: '보유·이용 기간',
      text: `수집한 개인정보는 ${DISPATCH_PRIVACY_POLICY.retentionPeriod} 보관한 뒤 삭제합니다. 동의 철회나 삭제 요청이 먼저 처리되면 그 시점에 개인정보를 삭제합니다.`,
    },
    {
      label: '동의 거부',
      text: '개인정보 수집·이용에 동의하지 않을 수 있지만, 이름과 연락처가 없으면 요청을 식별하고 문자로 견적을 안내할 수 없어 예상 견적 요청이 접수되지 않습니다.',
    },
    {
      label: '철회·삭제 요청과 파기',
      text: `동의 철회나 삭제는 ${DISPATCH_PRIVACY_POLICY.contactEmail}로 요청할 수 있습니다. 운영팀이 요청을 확인해 수동으로 처리하고 처리 결과를 기록합니다. 요청에 연결된 운영 기록을 포함한 전자적 파일은 복구할 수 없는 방법으로 삭제하며, 삭제 후에는 특정 개인을 알아볼 수 없는 통계만 남길 수 있습니다.`,
    },
    {
      label: '처리 주체',
      text: DISPATCH_PRIVACY_POLICY.controller,
    },
    {
      label: '문의',
      text: DISPATCH_PRIVACY_POLICY.contactEmail,
    },
    {
      label: '안내문 적용일',
      text: DISPATCH_PRIVACY_POLICY.effectiveDate,
    },
    {
      label: '안내문 버전',
      text: DISPATCH_PRIVACY_POLICY.version,
    },
  ] satisfies PrivacyConsentSection[],
} as const;
