export type SignupRequest = {
    loginId: string;
    password: string;
    phoneNumber: string;
    address: string;
};

export type SignupResponse = {
    id: number;
    loginId: string;
    role: 'MEMBER';
};

export type LoginRequest = {
    loginId: string;
    password: string;
};

export type LoginResponse = {
    token: string;
    role: 'MEMBER';
};

export type AuthErrorCode = 'DUPLICATE_LOGIN_ID' | 'INVALID_REQUEST' | 'LOGIN_FAILED' | 'INVALID_TOKEN';

type ErrorResponse = {
    code: AuthErrorCode;
    message: string;
};

export class AuthApiError extends Error {
    readonly code: AuthErrorCode;

    constructor({ code, message }: ErrorResponse) {
        super(message);
        this.name = 'AuthApiError';
        this.code = code;
    }
}

function isErrorResponse(body: unknown): body is ErrorResponse {
    return typeof body === 'object'
        && body !== null
        && 'code' in body
        && 'message' in body
        && typeof body.code === 'string'
        && typeof body.message === 'string';
}

export async function signup(request: SignupRequest): Promise<SignupResponse> {
    const response = await fetch('/api/auth/signup', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(request)
    });
    const body: unknown = await response.json();

    if (!response.ok) {
        if (isErrorResponse(body)) {
            throw new AuthApiError(body);
        }

        throw new Error('회원가입에 실패했습니다.');
    }

    return body as SignupResponse;
}

export type MemberMe = {
    id: number;
    loginId: string;
    role: 'MEMBER';
    phoneNumber: string;
    address: string;
};

export async function fetchMe(): Promise<MemberMe> {
    const token = window.sessionStorage.getItem('setty:auth-token');
    const response = await fetch('/api/auth/me', {
        headers: token ? { Authorization: `Bearer ${token}` } : {}
    });
    const body: unknown = await response.json();

    if (!response.ok) {
        if (isErrorResponse(body)) {
            throw new AuthApiError(body);
        }

        throw new Error('내 정보를 불러오지 못했습니다. 로그인 상태를 확인해 주세요.');
    }

    return body as MemberMe;
}

export async function login(request: LoginRequest): Promise<LoginResponse> {
    const response = await fetch('/api/auth/login', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(request)
    });
    const body: unknown = await response.json();

    if (!response.ok) {
        if (isErrorResponse(body)) {
            throw new AuthApiError(body);
        }

        throw new Error('로그인에 실패했습니다.');
    }

    return body as LoginResponse;
}
