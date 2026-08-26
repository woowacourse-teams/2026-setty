/**
 * 런타임 설정. Expo의 EXPO_PUBLIC_* 환경 변수로 주입한다.
 *
 * - apiBaseUrl 이 비어 있으면 useMock=true 가 되어 목 응답으로 동작한다.
 *   (배차 API dev 베이스 URL이 아직 없어 실기기 구동은 목으로 확인한다.)
 * - 실제 서버가 준비되면 EXPO_PUBLIC_API_BASE_URL 만 채우면 실 fetch로 전환된다.
 * - authUuid 는 향후 UUID 헤더 인증 주입 지점이다(현재 미사용, 다음 이슈).
 */
export const config = {
  apiBaseUrl: process.env.EXPO_PUBLIC_API_BASE_URL ?? '',
  authUuid: process.env.EXPO_PUBLIC_AUTH_UUID ?? '',
  get useMock(): boolean {
    return this.apiBaseUrl.trim() === '';
  },
};
