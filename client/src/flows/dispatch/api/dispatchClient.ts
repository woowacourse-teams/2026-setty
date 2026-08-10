import { API_ORIGIN } from '@/shared/api/http';

/**
 * dispatch flow 전용 HTTP 클라이언트다.
 * server `GlobalExceptionHandler`가 오류를 `{ "message": string }`으로 돌려주므로
 * 그 형태만 신뢰하고, 실패를 성공으로 바꿔 표시하지 않는다.
 */

export class DispatchApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'DispatchApiError';
    this.status = status;
  }
}

const DEFAULT_ERROR_MESSAGE = '요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.';
const NETWORK_ERROR_MESSAGE = '네트워크에 연결하지 못했어요. 연결을 확인해 주세요.';

const readErrorMessage = async (response: Response): Promise<string> => {
  try {
    const body: unknown = await response.json();
    if (
      typeof body === 'object' &&
      body !== null &&
      'message' in body &&
      typeof (body as { message: unknown }).message === 'string'
    ) {
      return (body as { message: string }).message;
    }
  } catch {
    // 오류 본문이 JSON이 아니면 기본 문구를 쓴다.
  }

  return DEFAULT_ERROR_MESSAGE;
};

async function request<TResponse>(
  path: string,
  init?: { method?: string; body?: unknown },
): Promise<TResponse> {
  const method = init?.method ?? 'GET';
  const hasBody = init?.body !== undefined;

  let response: Response;
  try {
    response = await fetch(`${API_ORIGIN}${path}`, {
      method,
      headers: hasBody ? { 'Content-Type': 'application/json' } : undefined,
      body: hasBody ? JSON.stringify(init?.body) : undefined,
    });
  } catch {
    throw new DispatchApiError(0, NETWORK_ERROR_MESSAGE);
  }

  if (!response.ok) {
    throw new DispatchApiError(response.status, await readErrorMessage(response));
  }

  if (response.status === 204) {
    return undefined as TResponse;
  }

  return (await response.json()) as TResponse;
}

/**
 * `.ts`에도 JSX 파서가 적용되므로 화살표 함수 제네릭(`<T>(...) => ...`) 대신
 * method 축약 문법을 쓴다.
 */
export const dispatchClient = {
  get<TResponse>(path: string): Promise<TResponse> {
    return request<TResponse>(path);
  },
  /** 본문 없는 상태 전환 POST가 있어 `body`는 선택이다. */
  post<TResponse>(path: string, body?: unknown): Promise<TResponse> {
    return request<TResponse>(path, { method: 'POST', body });
  },
};
