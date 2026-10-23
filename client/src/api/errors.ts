import { ApiError, NetworkError } from './http';
import { ListingImageTooLargeError } from './repositories';

const INVALID_TOKEN = 'INVALID_TOKEN';
const MEMBER_DONG_REQUIRED = 'MEMBER_DONG_REQUIRED';

/** 토큰이 없거나 만료돼 다시 로그인해야 하는 에러인지 여부. */
export function isUnauthorized(error: unknown): boolean {
    return error instanceof ApiError && error.code === INVALID_TOKEN;
}

/** 회원 행정동을 정하지 않아 정보 수정 화면으로 보내야 하는 에러인지 여부. */
export function isMemberDongRequired(error: unknown): boolean {
    return error instanceof ApiError && error.code === MEMBER_DONG_REQUIRED;
}

/** 사용자에게 보여줄 에러 문구. 서버 에러는 서버가 내려준 문구를 그대로 쓴다. */
export function toUserMessage(error: unknown, fallback = '알 수 없는 에러가 발생했습니다'): string {
    if (error instanceof ApiError) return isUnauthorized(error) ? '로그인이 필요합니다' : error.message;
    if (error instanceof ListingImageTooLargeError) return error.message;
    if (error instanceof NetworkError) return error.message;
    return fallback;
}

/** 서버에서 한 번에 불러와 보여주는 화면의 상태. */
export type LoadState<T> =
    | { status: 'loading' }
    | { status: 'success'; data: T }
    | { status: 'error'; message: string; isUnauthorized: boolean };

export function toLoadError(error: unknown): LoadState<never> {
    return { status: 'error', message: toUserMessage(error), isUnauthorized: isUnauthorized(error) };
}
