import { HttpError } from './http';

/**
 * 서버 에러 코드 → 사용자 문구. (서버 공통 `{ code, message }`)
 * 목록에 없는 코드는 fallback 문구로 처리한다.
 */
const MESSAGES: Record<string, string> = {
  // 인증
  DUPLICATE_LOGIN_ID: '이미 사용 중인 아이디예요',
  LOGIN_FAILED: '아이디 또는 비밀번호를 확인해 주세요',
  INVALID_TOKEN: '로그인이 만료됐어요. 다시 로그인해 주세요',
  // 배차
  DELIVERY_NOT_FOUND: '배차 정보를 찾을 수 없어요',
  DELIVERY_DRIVER_MISMATCH: '내 배차가 아니에요',
  DELIVERY_ALREADY_ACCEPTED: '이미 다른 기사가 수락한 배차예요',
  ORDER_DELIVERY_STATUS_MISMATCH: '지금은 처리할 수 없는 상태예요',
};

/**
 * 에러를 사용자 문구로 바꾼다.
 * - INVALID_REQUEST: 서버가 "<필드>: <메시지>" 형태로 내려주므로 그 문구를 그대로 쓴다.
 * - 그 외 known 코드: 매핑 문구.
 * - 미지의 에러: fallback.
 */
export function errorMessage(error: unknown, fallback: string): string {
  if (error instanceof HttpError) {
    if (error.code === 'INVALID_REQUEST') return error.message || fallback;
    if (error.code && MESSAGES[error.code]) return MESSAGES[error.code];
    return error.message || fallback;
  }
  return error instanceof Error && error.message ? error.message : fallback;
}
