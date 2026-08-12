const STORAGE_KEY = 'setty.onboarding.completed';
const COMPLETED_VALUE = 'true';

/**
 * 저장이 막힌 브라우저(사파리 프라이빗, 저장소 차단, 용량 초과)에서도
 * 홈과 온보딩 사이를 오가는 리다이렉트 루프가 생기지 않도록 하는 폴백이다.
 * 저장에 실패했을 때만 채우므로 localStorage가 정상인 환경에서는 쓰이지 않는다.
 */
const fallbackStore = new Map<string, string>();

/** 이 기기에서 온보딩을 이미 봤는지 확인한다. */
export function isOnboardingCompleted(): boolean {
  if (fallbackStore.get(STORAGE_KEY) === COMPLETED_VALUE) {
    return true;
  }

  try {
    return window.localStorage.getItem(STORAGE_KEY) === COMPLETED_VALUE;
  } catch {
    // 읽기 자체가 막힌 환경에서는 아직 보지 않은 것으로 본다.
    return false;
  }
}

/** 온보딩을 끝냈거나 건너뛴 시점에 호출해 다음 방문부터 노출하지 않는다. */
export function completeOnboarding(): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, COMPLETED_VALUE);
  } catch {
    // 저장이 막힌 환경에서는 이번 방문 동안만 유지한다.
    fallbackStore.set(STORAGE_KEY, COMPLETED_VALUE);
  }
}
