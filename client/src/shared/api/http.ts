const DEFAULT_LOCAL_API_ORIGIN = 'http://localhost:8080';

const configuredApiOrigin = process.env.SETTY_API_BASE_URL?.trim();
const defaultApiOrigin =
  process.env.NODE_ENV === 'production' ? '' : DEFAULT_LOCAL_API_ORIGIN;

export const API_ORIGIN = (configuredApiOrigin || defaultApiOrigin).replace(/\/+$/, '');

export interface ApiErrorBody {
  code?: string;
  message?: string;
  fieldErrors?: Record<string, string>;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly fieldErrors?: Record<string, string>;

  constructor(status: number, body?: ApiErrorBody) {
    super(body?.message || '요청을 처리하지 못했습니다.');
    this.name = 'ApiError';
    this.status = status;
    this.code = body?.code;
    this.fieldErrors = body?.fieldErrors;
  }
}

async function readErrorBody(response: Response): Promise<ApiErrorBody | undefined> {
  const contentType = response.headers.get('content-type');
  if (!contentType?.includes('application/json')) {
    return undefined;
  }

  try {
    return (await response.json()) as ApiErrorBody;
  } catch {
    return undefined;
  }
}

export async function requestJson<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set('Accept', 'application/json');
  if (init.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  const response = await fetch(`${API_ORIGIN}${path}`, {
    ...init,
    credentials: init.credentials ?? 'same-origin',
    headers,
  });

  if (!response.ok) {
    throw new ApiError(response.status, await readErrorBody(response));
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}
