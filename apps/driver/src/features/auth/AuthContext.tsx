import { createContext, ReactNode, useCallback, useContext, useEffect, useState } from 'react';
import { setOnUnauthorized } from '@/lib/http';
import { tokenStore } from '@/lib/tokenStore';

type Status = 'loading' | 'authed' | 'guest';

interface AuthValue {
  status: Status;
  /** 로그인 성공 토큰을 저장하고 인증 상태로 전환한다. */
  signIn: (token: string) => Promise<void>;
  /** 토큰을 지우고 게스트로 전환한다. */
  signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthValue | null>(null);

/**
 * 앱 전역 인증 상태.
 * - 시작 시 SecureStore에서 토큰을 읽어 authed/guest를 정한다.
 * - 서버가 토큰 만료(INVALID_TOKEN)를 알리면 자동으로 guest로 떨어뜨린다.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<Status>('loading');

  useEffect(() => {
    let mounted = true;

    void tokenStore.load().then((token) => {
      if (mounted) setStatus(token ? 'authed' : 'guest');
    });

    // 토큰 만료 감지(http 계층) → 전역 로그아웃
    setOnUnauthorized(() => {
      void tokenStore.clear();
      setStatus('guest');
    });

    return () => {
      mounted = false;
      setOnUnauthorized(null);
    };
  }, []);

  const signIn = useCallback(async (token: string) => {
    await tokenStore.save(token);
    setStatus('authed');
  }, []);

  const signOut = useCallback(async () => {
    await tokenStore.clear();
    setStatus('guest');
  }, []);

  return <AuthContext.Provider value={{ status, signIn, signOut }}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
