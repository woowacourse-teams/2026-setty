import * as SecureStore from 'expo-secure-store';

/**
 * 배송원 인증 토큰 저장소.
 *
 * - 영속: expo-secure-store(기기 키체인). 앱을 껐다 켜도 로그인 유지.
 * - 메모리 캐시: http 헤더는 동기적으로 토큰이 필요하므로 메모리에도 둔다.
 *   앱 시작 시 load()로 SecureStore → 메모리로 한 번 끌어온다.
 */

const KEY = 'delivery.authToken';

let memToken: string | null = null;

export const tokenStore = {
  /** 앱 시작 시 1회. 저장된 토큰을 메모리로 로드하고 반환한다. */
  async load(): Promise<string | null> {
    try {
      memToken = await SecureStore.getItemAsync(KEY);
    } catch {
      memToken = null;
    }
    return memToken;
  },

  /** http 헤더 주입용 동기 조회. load() 이후 유효하다. */
  get(): string | null {
    return memToken;
  },

  async save(token: string): Promise<void> {
    // 메모리 토큰이 http 인증의 실제 소스다. 영속(SecureStore)은 최선 노력으로,
    // 실패해도 인증 흐름을 막지 않는다(다음 실행에서 재로그인만 필요).
    memToken = token;
    try {
      await SecureStore.setItemAsync(KEY, token);
    } catch (e) {
      console.warn('[tokenStore] persist failed', e);
    }
  },

  async clear(): Promise<void> {
    memToken = null;
    await SecureStore.deleteItemAsync(KEY);
  },
};
