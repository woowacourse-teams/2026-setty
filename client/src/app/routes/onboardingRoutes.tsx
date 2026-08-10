import { Navigate, type RouteObject, useNavigate, useParams } from 'react-router-dom';
import OnboardingScreen from '@/app/onboarding/OnboardingScreen';
import { findOnboardingStep } from '@/app/onboarding/onboardingSteps';
import { completeOnboarding } from '@/app/onboarding/onboardingStorage';

/**
 * 온보딩 단계도 URL로 표현한다.
 * 단계를 컴포넌트 state로만 들고 있으면 히스토리 항목이 쌓이지 않아
 * 뒤로가기가 이전 단계 대신 사이트 밖으로 나간다.
 */
export const ONBOARDING_PATH = {
  step: (stepNumber: number) => `/onboarding/${stepNumber}`,
} as const;

/** 온보딩을 끝낸 뒤 이동할 수 있는 경로. flow 코드를 import하지 않고 경로만 참조한다. */
const HOME_PATH = '/';
const ESTIMATE_PATH = '/estimate';
const DISPATCH_FORM_PATH = '/dispatch/new';

function OnboardingStepRoute() {
  const navigate = useNavigate();
  const params = useParams<{ step: string }>();
  const stepNumber = Number(params.step);
  const step = findOnboardingStep(stepNumber);

  // 없는 단계 URL로 들어오면 오류 화면 대신 온보딩 첫 화면으로 되돌린다.
  if (!step) {
    return <Navigate to={ONBOARDING_PATH.step(1)} replace />;
  }

  /*
   * 온보딩을 벗어나는 순간 완료로 기록한다.
   * 지금 단계를 홈으로 덮어쓴 뒤 목적지를 쌓아,
   * 나간 화면에서 뒤로가면 방금 본 단계가 아니라 홈에 도착한다.
   */
  const leaveOnboarding = (path: string) => {
    completeOnboarding();
    navigate(HOME_PATH, { replace: true });

    if (path !== HOME_PATH) {
      navigate(path);
    }
  };

  return (
    <OnboardingScreen
      key={stepNumber}
      stepNumber={stepNumber}
      step={step}
      onNext={() => navigate(ONBOARDING_PATH.step(stepNumber + 1))}
      onSkip={() => leaveOnboarding(HOME_PATH)}
      onCheckEstimate={() => leaveOnboarding(ESTIMATE_PATH)}
      onCreateLink={() => leaveOnboarding(DISPATCH_FORM_PATH)}
    />
  );
}

export const onboardingRoutes: RouteObject[] = [
  {
    path: '/onboarding',
    element: <Navigate to={ONBOARDING_PATH.step(1)} replace />,
  },
  {
    path: '/onboarding/:step',
    element: <OnboardingStepRoute />,
  },
];
