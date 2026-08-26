import { config } from './config';

/**
 * 얇은 fetch 래퍼. 실제 서버 연동 경로다(목 분기는 api 레이어에서 처리).
 *
 * - 목록 응답은 bare 배열/`{ items: [...] }` 두 형태를 모두 허용한다
 *   (배차 목록 응답 봉투가 미확정 — 확정되면 좁힌다. apps/docs 참고).
 * - 인증 헤더(UUID)는 authUuid가 있을 때만 붙인다. 헤더 이름은 미확정이라
 *   확정되면 AUTH_HEADER 만 바꾸면 된다.
 */

const AUTH_HEADER = 'X-User-Id'; // TODO(#후속): UUID 헤더 이름 확정 시 교체

export class HttpError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = 'HttpError';
  }
}

function headers(): Record<string, string> {
  const h: Record<string, string> = { 'Content-Type': 'application/json' };
  if (config.authUuid) h[AUTH_HEADER] = config.authUuid;
  return h;
}

async function parse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    throw new HttpError(res.status, `요청 실패 (${res.status})`);
  }
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return text ? (JSON.parse(text) as T) : (undefined as T);
}

export async function httpGet<T>(path: string): Promise<T> {
  const res = await fetch(config.apiBaseUrl + path, { headers: headers() });
  return parse<T>(res);
}

/** 목록 응답 봉투(bare 배열 또는 { items }) 어느 쪽이든 배열로 되돌린다. */
export async function httpGetList<T>(path: string): Promise<T[]> {
  const data = await httpGet<T[] | { items: T[] }>(path);
  return Array.isArray(data) ? data : (data?.items ?? []);
}

export async function httpPost<T>(path: string, body?: unknown): Promise<T> {
  const res = await fetch(config.apiBaseUrl + path, {
    method: 'POST',
    headers: headers(),
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  return parse<T>(res);
}
