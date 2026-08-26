import { config } from './config';
import { tokenStore } from './tokenStore';

/**
 * 얇은 fetch 래퍼. 실제 서버 연동 경로다(목 분기는 api 레이어에서 처리).
 *
 * - 인증: 저장된 토큰이 있으면 `Authorization: Bearer <token>`을 붙인다.
 * - 에러 바디는 서버 공통 형식 `{ code, message }`로 파싱해 HttpError에 담는다.
 *   code === 'INVALID_TOKEN'(401)이면 등록된 onUnauthorized 훅을 호출해
 *   전역 로그아웃을 유도한다(로그인 유도는 화면 계층이 담당).
 * - 목록 응답은 bare 배열/`{ items: [...] }` 두 형태를 모두 허용한다.
 */

export class HttpError extends Error {
  constructor(
    readonly status: number,
    message: string,
    /** 서버 에러 코드(있을 때만). 화면 문구 매핑에 쓴다. */
    readonly code?: string,
  ) {
    super(message);
    this.name = 'HttpError';
  }
}

/** 토큰 만료(INVALID_TOKEN) 감지 시 호출할 훅. AuthProvider가 등록한다. */
let onUnauthorized: (() => void) | null = null;
export function setOnUnauthorized(handler: (() => void) | null): void {
  onUnauthorized = handler;
}

function headers(): Record<string, string> {
  const h: Record<string, string> = { 'Content-Type': 'application/json' };
  const token = tokenStore.get();
  if (token) h.Authorization = `Bearer ${token}`;
  return h;
}

async function readErrorBody(res: Response): Promise<{ code?: string; message?: string }> {
  try {
    const text = await res.text();
    if (!text) return {};
    return JSON.parse(text) as { code?: string; message?: string };
  } catch {
    return {};
  }
}

async function parse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const body = await readErrorBody(res);
    if (body.code === 'INVALID_TOKEN') onUnauthorized?.();
    throw new HttpError(res.status, body.message ?? `요청 실패 (${res.status})`, body.code);
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
