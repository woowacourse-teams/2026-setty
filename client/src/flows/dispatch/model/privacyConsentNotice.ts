interface PrivacyConsentSection {
  label: string;
  text: string;
  /** 시안에서 굵게 강조된 항목 */
  emphasis?: boolean;
}

/**
 * 시안 `개인정보 수집·이용 동의` 화면의 문구다.
 * 보유·이용 기간은 시안에 적힌 값을 그대로 쓰며, 실제 사용자 접수 전에 팀이 확정해야 한다.
 */
export const DISPATCH_PRIVACY_CONSENT_NOTICE = {
  title: ['안전한 거래 중개를 위해', '아래 정보를 수집해요'],
  sections: [
    {
      label: '수집 항목',
      text: '이름, 연락처, 받는 주소, 거래 정보',
      emphasis: true,
    },
    {
      label: '이용 목적',
      text: '거래 상대방과의 중개, 대금 보관·정산, 배송 및 고객 안내',
    },
    {
      label: '보유·이용 기간',
      text: '거래 완료 후 5년까지 보관하며, 관련 법령에 따라 안전하게 파기해요',
    },
  ] satisfies PrivacyConsentSection[],
} as const;
