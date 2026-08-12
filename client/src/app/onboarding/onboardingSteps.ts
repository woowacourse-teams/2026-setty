export interface OnboardingStep {
  /** 제목 줄바꿈 위치가 시안에 고정돼 있어 줄 단위로 둔다. */
  titleLines: string[];
  description: string;
}

/**
 * 시안 카피를 그대로 쓰되 가격 계산·문자·차량 섭외를 자동 처리로 읽히게 하는 표현은 넣지 않는다.
 * 현재 MVP에서 이 과정은 운영자가 수동으로 진행한다.
 */
export const ONBOARDING_STEPS: OnboardingStep[] = [
  {
    titleLines: ['번거로운 중고 가구 거래,', 'SETTY가 도와드려요'],
    description:
      '개인정보 노출, 지루한 대화, 용달 예약까지 — 귀찮은 건 SETTY가 대신할게요. 조건만 정하면 거래가 끝까지 이어져요.',
  },
  {
    titleLines: ['번호도 주소도', '서로 몰라도 돼요'],
    description:
      '중고 가구를 사고팔 때 연락처·집주소를 상대에게 알려주지 않아요. SETTY만 알고 연결해 드려요.',
  },
  {
    titleLines: ['큰 가구도 용달까지', 'SETTY가 불러드려요'],
    description:
      '배송 차량 섭외부터 픽업·전달까지 한 번에. 견적부터 부담 없이 확인해보세요.',
  },
];

export const ONBOARDING_STEP_COUNT = ONBOARDING_STEPS.length;

/** URL의 `:step`은 사용자에게 보이는 1부터 시작하는 번호다. 범위 밖이면 undefined다. */
export const findOnboardingStep = (stepNumber: number): OnboardingStep | undefined =>
  Number.isInteger(stepNumber) ? ONBOARDING_STEPS[stepNumber - 1] : undefined;
