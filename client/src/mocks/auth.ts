import { http, HttpResponse } from 'msw';
import type { SignupRequest, SignupResponse } from '../api/auth';

type MockMember = SignupResponse & {
    password: string;
    phoneNumber: string;
    address: string;
    token: string | null;
};

const loginIdPattern = /^[a-z0-9]{4,20}$/;
const phoneNumberPattern = /^010-\d{4}-\d{4}$/;
const members: MockMember[] = [];
let nextMemberId = 1;

function invalidRequest() {
    return HttpResponse.json(
        { code: 'INVALID_REQUEST', message: '잘못된 요청입니다.' },
        { status: 400 }
    );
}

function isValidSignupRequest(body: unknown): body is SignupRequest {
    if (typeof body !== 'object' || body === null) {
        return false;
    }

    const { loginId, password, phoneNumber, address } = body as Partial<SignupRequest>;
    return typeof loginId === 'string'
        && loginIdPattern.test(loginId)
        && typeof password === 'string'
        && password.length >= 8
        && password.length <= 64
        && typeof phoneNumber === 'string'
        && phoneNumberPattern.test(phoneNumber)
        && typeof address === 'string'
        && address.length > 0
        && address.length <= 200;
}

function isValidLoginRequest(body: unknown): body is { loginId: string; password: string } {
    if (typeof body !== 'object' || body === null) {
        return false;
    }

    const { loginId, password } = body as { loginId?: unknown; password?: unknown };
    return typeof loginId === 'string'
        && loginId.trim().length > 0
        && typeof password === 'string'
        && password.trim().length > 0;
}

export const authHandlers = [
    http.post('/api/auth/signup', async ({ request }) => {
        let body: unknown;

        try {
            body = await request.json();
        } catch {
            return invalidRequest();
        }

        if (!isValidSignupRequest(body)) {
            return invalidRequest();
        }

        if (members.some((member) => member.loginId === body.loginId)) {
            return HttpResponse.json(
                { code: 'DUPLICATE_LOGIN_ID', message: '이미 사용 중인 아이디입니다.' },
                { status: 400 }
            );
        }

        const member: MockMember = {
            id: nextMemberId++,
            loginId: body.loginId,
            password: body.password,
            phoneNumber: body.phoneNumber,
            address: body.address,
            token: null,
            role: 'MEMBER'
        };
        members.push(member);

        return HttpResponse.json({
            id: member.id,
            loginId: member.loginId,
            role: member.role
        }, { status: 201 });
    }),

    http.post('/api/auth/login', async ({ request }) => {
        let body: unknown;

        try {
            body = await request.json();
        } catch {
            return invalidRequest();
        }

        if (!isValidLoginRequest(body)) {
            return invalidRequest();
        }

        const member = members.find((candidate) => candidate.loginId === body.loginId);
        if (!member || member.password !== body.password) {
            return HttpResponse.json(
                { code: 'LOGIN_FAILED', message: '아이디 또는 비밀번호가 일치하지 않습니다.' },
                { status: 401 }
            );
        }

        member.token = crypto.randomUUID();

        return HttpResponse.json({
            token: member.token,
            role: member.role
        });
    }),

    http.get('/api/auth/me', ({ request }) => {
        const token = request.headers.get('Authorization')?.replace('Bearer ', '') ?? null;
        const member = token ? members.find((candidate) => candidate.token === token) : undefined;
        if (!member) {
            return HttpResponse.json({ code: 'INVALID_TOKEN', message: '로그인이 필요합니다.' }, { status: 401 });
        }

        return HttpResponse.json({
            id: member.id,
            loginId: member.loginId,
            role: member.role,
            phoneNumber: member.phoneNumber,
            address: member.address
        });
    })
];
