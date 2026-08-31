export type PaymentConfirmRequest = {
    listingId: number;
    tossOrderId: string;
    paymentKey: string;
    amount: number;
};

export type PaymentConfirm = {
    paymentId: number;
    orderId: number;
    amount: number;
    status: string;
};

export type PaymentErrorCode =
    | 'PAYMENT_AMOUNT_MISMATCH'
    | 'PAYMENT_CONFIRM_FAILED'
    | 'ALREADY_PAID'
    | 'ALREADY_ORDERED'
    | 'CANNOT_ORDER_OWN_LISTING'
    | 'LISTING_NOT_FOUND'
    | 'INVALID_TOKEN'
    | 'INVALID_REQUEST';

type ErrorResponse = {
    code: PaymentErrorCode;
    message: string;
};

export class PaymentApiError extends Error {
    readonly code: PaymentErrorCode;

    constructor({ code, message }: ErrorResponse) {
        super(message);
        this.name = 'PaymentApiError';
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
        if (isErrorResponse(body)) throw new PaymentApiError(body);
    } catch (error) {
        if (error instanceof PaymentApiError) throw error;
    }

    throw new Error(fallbackMessage);
}

export async function confirmPayment(request: PaymentConfirmRequest): Promise<PaymentConfirm> {
    const response = await fetch('/api/payments/confirm', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            ...authorizationHeaders()
        },
        body: JSON.stringify(request)
    });
    await ensureSuccess(response, '결제 승인을 완료하지 못했습니다.');
    return response.json() as Promise<PaymentConfirm>;
}
