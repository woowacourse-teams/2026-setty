import {
  LoginRequest,
  LoginResponse,
  SignupRequest,
  SignupResponse,
} from '@/model/auth';
import { config } from '@/lib/config';
import { httpPost } from '@/lib/http';
import { authMock } from './mock/authMock';

/**
 * 배송원 인증 API. useMock(베이스 URL 없음)이면 목으로, 아니면 실 fetch로 라우팅한다.
 * signup/login 두 엔드포인트는 인증이 필요 없다(공개).
 */
export const deliveryAuthApi = {
  /** POST /api/delivery/auth/login → { token } */
  login(request: LoginRequest): Promise<LoginResponse> {
    return config.useMock
      ? authMock.login(request)
      : httpPost<LoginResponse>('/api/delivery/auth/login', request);
  },

  /** POST /api/delivery/auth/signup → { id, loginId } (201) */
  signup(request: SignupRequest): Promise<SignupResponse> {
    return config.useMock
      ? authMock.signup(request)
      : httpPost<SignupResponse>('/api/delivery/auth/signup', request);
  },
};
