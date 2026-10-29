function createTokenStore(key: string) {
    return {
        get(): string {
            try {
                return window.localStorage.getItem(key) ?? '';
            } catch {
                return '';
            }
        },
        set(token: string) {
            try {
                window.localStorage.setItem(key, token);
            } catch {
                // 저장소를 쓸 수 없으면 이번 화면에서만 로그인한 것으로 본다
            }
        },
        clear() {
            try {
                window.localStorage.removeItem(key);
            } catch {
                // 저장소를 쓸 수 없으면 지울 토큰도 없다
            }
        }
    };
}

/** 로그인 토큰. Android 앱처럼 브라우저를 닫았다 열어도 로그인을 유지한다. */
export const tokenStore = createTokenStore('setty:credential-token');

/** 배송원 로그인 토큰. 회원 토큰과 따로 두어 한 브라우저에서 구매자와 배송원으로 함께 로그인할 수 있다. */
export const deliveryTokenStore = createTokenStore('setty:delivery-token');

/** 배송원 로그인 아이디. 배송원 내 정보 API가 없어 프로필 앞글자를 보여 주려고 로그인할 때 함께 남긴다. */
export const deliveryLoginIdStore = createTokenStore('setty:delivery-login-id');

/** 서버가 `{ code, message }`로 내려준 에러. */
export class ApiError extends Error {
    readonly code: string;
    readonly status: number;

    constructor(code: string, message: string, status: number) {
        super(message);
        this.name = 'ApiError';
        this.code = code;
        this.status = status;
    }
}

/** 서버에 닿지 못했거나 응답을 읽지 못한 에러. */
export class NetworkError extends Error {
    constructor() {
        super('네트워크 에러가 발생했습니다');
        this.name = 'NetworkError';
    }
}

type RequestOptions = {
    method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
    body?: unknown;
    query?: Record<string, string | number | null | undefined>;
    // 어느 계정의 토큰을 붙일지. 배송원 API는 'delivery'
    auth?: 'member' | 'delivery';
};

function isErrorBody(body: unknown): body is { code: string; message: string } {
    return typeof body === 'object'
        && body !== null
        && 'code' in body
        && 'message' in body
        && typeof body.code === 'string'
        && typeof body.message === 'string';
}

/** API를 호출하고 서버 에러 응답을 [ApiError]로, 연결 실패를 [NetworkError]로 바꿔 던진다. */
export async function apiCall<T>(path: string, { method = 'GET', body, query, auth = 'member' }: RequestOptions = {}): Promise<T> {
    const url = new URL(path, window.location.origin);
    Object.entries(query ?? {}).forEach(([key, value]) => {
        if (value !== null && value !== undefined) url.searchParams.set(key, String(value));
    });

    const headers: Record<string, string> = {};
    const token = (auth === 'delivery' ? deliveryTokenStore : tokenStore).get();
    if (token) headers.Authorization = `Bearer ${token}`;

    let requestBody: BodyInit | undefined;
    if (body instanceof FormData) {
        requestBody = body;
    } else if (body !== undefined) {
        headers['Content-Type'] = 'application/json';
        requestBody = JSON.stringify(body);
    }

    let response: Response;
    try {
        response = await fetch(url, { method, headers, body: requestBody });
    } catch {
        throw new NetworkError();
    }

    const text = await response.text().catch(() => '');
    let parsed: unknown = undefined;
    if (text) {
        try {
            parsed = JSON.parse(text);
        } catch {
            parsed = undefined;
        }
    }

    if (!response.ok) {
        if (isErrorBody(parsed)) throw new ApiError(parsed.code, parsed.message, response.status);
        throw new ApiError('UNKNOWN', '알 수 없는 에러가 발생했습니다', response.status);
    }

    return parsed as T;
}
