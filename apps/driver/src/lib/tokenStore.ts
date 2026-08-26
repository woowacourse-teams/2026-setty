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
    memToken = token;
    await SecureStore.setItemAsync(KEY, token);
  },

  async clear(): Promise<void> {
    memToken = null;
    await SecureStore.deleteItemAsync(KEY);
  },
};
