import { ESTIMATE_PRIVACY_POLICY } from './estimatePrivacyPolicy';

interface PrivacyConsentSection {
  label: string;
  text: string;
  /** 시안에서 굵게 강조된 항목 */
  emphasis?: boolean;
}

/**
 * 시안 `개인정보 수집·이용 동의` 화면의 문구다.
 * 항목·목적·기간은 예상 견적 흐름이 실제로 받는 입력과 확정된 보유 기간만 적는다.
 */
export const ESTIMATE_PRIVACY_CONSENT_NOTICE = {
  title: ['안전한 견적 안내를 위해', '아래 정보를 수집해요'],
  sections: [
    {
      label: '수집 항목',
      text: ESTIMATE_PRIVACY_POLICY.items,
      emphasis: true,
    },
    {
      label: '이용 목적',
      text: ESTIMATE_PRIVACY_POLICY.purpose,
    },
    {
      label: '보유·이용 기간',
      text: `${ESTIMATE_PRIVACY_POLICY.retentionPeriod} 보관하며, 관련 법령에 따라 안전하게 파기해요`,
    },
  ] satisfies PrivacyConsentSection[],
} as const;
