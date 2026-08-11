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
  /*
   * multipart 요청은 `Content-Type`을 직접 지정하면 boundary가 빠져 server가 본문을 읽지 못한다.
   * FormData를 그대로 넘겨 브라우저가 헤더를 만들게 둔다.
   */
  const isFormData = init?.body instanceof FormData;
  const body = isFormData ? (init?.body as FormData) : JSON.stringify(init?.body);

  let response: Response;
  try {
    response = await fetch(`${API_ORIGIN}${path}`, {
      method,
      headers: hasBody && !isFormData ? { 'Content-Type': 'application/json' } : undefined,
      body: hasBody ? body : undefined,
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
  /** 파일 업로드용 multipart POST다. */
  postForm<TResponse>(path: string, body: FormData): Promise<TResponse> {
    return request<TResponse>(path, { method: 'POST', body });
  },
};
