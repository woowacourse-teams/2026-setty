import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { isOnboardingCompleted } from './onboardingStorage';
import { ONBOARDING_PATH } from '@/app/routes/onboardingRoutes';

interface OnboardingGateProps {
  children: ReactNode;
}

/**
 * 홈에 처음 들어온 기기에만 온보딩을 보여준다.
 * 토큰 링크로 바로 들어오는 판매자·구매자 화면은 감싸지 않는다.
 */
export default function OnboardingGate({ children }: OnboardingGateProps) {
  if (!isOnboardingCompleted()) {
    return <Navigate to={ONBOARDING_PATH.step(1)} replace />;
  }

  return <>{children}</>;
}
