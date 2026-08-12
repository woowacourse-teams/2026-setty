import { ApiError, requestJson } from '@/shared/api/http';
import { clearOperatorSecret, getOperatorSecret } from './operatorSecretStorage';

const OPERATOR_SECRET_HEADER = 'X-Operator-Secret';
const OPERATOR_ACCESS_CHECK_PATH = '/api/operator/auth';

interface OperatorAuthResponse {
  authenticated: boolean;
}

async function requestWithOperatorSecret<T>(
  path: string,
  secret: string,
  init: RequestInit = {},
): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set(OPERATOR_SECRET_HEADER, secret);

  try {
    return await requestJson<T>(path, {
      ...init,
      cache: 'no-store',
      credentials: 'omit',
      headers,
    });
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      clearOperatorSecret();
    }
    throw error;
  }
}

export async function validateOperatorSecret(secret: string): Promise<void> {
  const response = await requestWithOperatorSecret<OperatorAuthResponse>(
    OPERATOR_ACCESS_CHECK_PATH,
    secret,
  );

  if (!response.authenticated) {
    throw new ApiError(401, {
      code: 'UNAUTHORIZED',
      message: '운영자 인증에 실패했습니다.',
    });
  }
}

export function requestOperatorJson<T>(path: string, init: RequestInit = {}): Promise<T> {
  const secret = getOperatorSecret();
  if (!secret) {
    return Promise.reject(
      new ApiError(401, {
        code: 'UNAUTHORIZED',
        message: '운영자 인증 정보가 없습니다.',
      }),
    );
  }

  return requestWithOperatorSecret<T>(path, secret, init);
}
