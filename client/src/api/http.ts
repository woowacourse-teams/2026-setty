const TOKEN_KEY = 'setty:credential-token';

/** 로그인 토큰. Android 앱처럼 브라우저를 닫았다 열어도 로그인을 유지한다. */
export const tokenStore = {
    get(): string {
        try {
            return window.localStorage.getItem(TOKEN_KEY) ?? '';
        } catch {
            return '';
        }
    },
    set(token: string) {
        try {
            window.localStorage.setItem(TOKEN_KEY, token);
        } catch {
            // 저장소를 쓸 수 없으면 이번 화면에서만 로그인한 것으로 본다
        }
    },
    clear() {
        try {
            window.localStorage.removeItem(TOKEN_KEY);
        } catch {
            // 저장소를 쓸 수 없으면 지울 토큰도 없다
        }
    }
};

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
export async function apiCall<T>(path: string, { method = 'GET', body, query }: RequestOptions = {}): Promise<T> {
    const url = new URL(path, window.location.origin);
    Object.entries(query ?? {}).forEach(([key, value]) => {
        if (value !== null && value !== undefined) url.searchParams.set(key, String(value));
    });

    const headers: Record<string, string> = {};
    const token = tokenStore.get();
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
