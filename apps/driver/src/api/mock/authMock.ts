import { LoginRequest, LoginResponse, SignupRequest, SignupResponse } from '@/model/auth';
import { HttpError } from '@/lib/http';

/**
 * 서버 없이(useMock) 로그인·회원가입 화면 흐름을 확인하기 위한 목.
 * - 어떤 값이든(빈 값만 아니면) 로그인 성공으로 처리하고 가짜 토큰을 준다.
 * - 실제 인증·검증은 서버가 담당한다. 여기 로직을 계약으로 삼지 않는다.
 */

const delay = (ms = 500) => new Promise((r) => setTimeout(r, ms));

export const authMock = {
  async login({ loginId, password }: LoginRequest): Promise<LoginResponse> {
    await delay();
    if (!loginId || !password) {
      throw new HttpError(401, '아이디 또는 비밀번호를 확인해 주세요', 'LOGIN_FAILED');
    }
    return { token: `mock-token-${loginId}` };
  },

  async signup(request: SignupRequest): Promise<SignupResponse> {
    await delay();
    return { id: 1, loginId: request.loginId };
  },
};
