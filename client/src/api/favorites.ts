import type { ListingItem } from './listings';

export type FavoriteErrorCode = 'INVALID_TOKEN' | 'LISTING_NOT_FOUND' | 'INVALID_REQUEST';

type ErrorResponse = {
    code: FavoriteErrorCode;
    message: string;
};

export class FavoriteApiError extends Error {
    readonly code: FavoriteErrorCode;

    constructor({ code, message }: ErrorResponse) {
        super(message);
        this.name = 'FavoriteApiError';
        this.code = code;
    }
}

function authorizationHeaders(): Record<string, string> {
    const token = window.sessionStorage.getItem('setty:auth-token');
    return token ? { Authorization: `Bearer ${token}` } : {};
}

function isErrorResponse(body: unknown): body is ErrorResponse {
    return typeof body === 'object'
        && body !== null
        && 'code' in body
        && 'message' in body
        && typeof body.code === 'string'
        && typeof body.message === 'string';
}

async function ensureSuccess(response: Response, fallbackMessage: string) {
    if (response.ok) return;

    try {
        const body: unknown = await response.json();
        if (isErrorResponse(body)) throw new FavoriteApiError(body);
    } catch (error) {
        if (error instanceof FavoriteApiError) throw error;
    }

    throw new Error(fallbackMessage);
}

export async function fetchMyFavorites(): Promise<ListingItem[]> {
    const response = await fetch('/api/me/favorites', { headers: authorizationHeaders() });
    await ensureSuccess(response, '찜 목록을 불러오지 못했습니다. 로그인 상태를 확인해 주세요.');
    const body = await response.json() as { items: ListingItem[] };
    return body.items;
}

export async function fetchFavoriteStatus(listingId: number): Promise<boolean> {
    const response = await fetch(`/api/me/favorites/${listingId}`, { headers: authorizationHeaders() });
    await ensureSuccess(response, '찜 여부를 확인하지 못했습니다.');
    const body = await response.json() as { favorited: boolean };
    return body.favorited;
}

export async function addFavorite(listingId: number): Promise<void> {
    const response = await fetch(`/api/me/favorites/${listingId}`, {
        method: 'PUT',
        headers: authorizationHeaders()
    });
    await ensureSuccess(response, '찜을 완료하지 못했습니다.');
}

export async function removeFavorite(listingId: number): Promise<void> {
    const response = await fetch(`/api/me/favorites/${listingId}`, {
        method: 'DELETE',
        headers: authorizationHeaders()
    });
    await ensureSuccess(response, '찜 해제를 완료하지 못했습니다.');
}
