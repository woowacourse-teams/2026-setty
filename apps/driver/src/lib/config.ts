/**
 * 런타임 설정. Expo의 EXPO_PUBLIC_* 환경 변수로 주입한다.
 *
 * - apiBaseUrl 이 비어 있으면 useMock=true 가 되어 목 응답으로 동작한다.
 *   (서버 없이 실기기에서 화면·흐름을 확인할 때 사용.)
 * - 실서버 연동: EXPO_PUBLIC_API_BASE_URL 을 채우면 실 fetch로 전환된다.
 *   · iOS 시뮬레이터: http://localhost:8080
 *   · 실기기(Expo Go/iPhone): 개발 PC의 LAN IP (예: http://192.168.0.10:8080)
 * - 인증은 로그인 응답 토큰(Bearer)을 tokenStore에서 주입한다(config에 두지 않음).
 */
export const config = {
  apiBaseUrl: process.env.EXPO_PUBLIC_API_BASE_URL ?? '',
  get useMock(): boolean {
    return this.apiBaseUrl.trim() === '';
  },
};
