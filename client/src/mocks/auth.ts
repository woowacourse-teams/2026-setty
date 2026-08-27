import { http, HttpResponse } from 'msw';
import type { SignupRequest, SignupResponse } from '../api/auth';

type MockMember = SignupResponse & {
    password: string;
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
            role: 'MEMBER'
        };
        members.push(member);

        return HttpResponse.json({
            id: member.id,
            loginId: member.loginId,
            role: member.role
        }, { status: 201 });
    })
];
