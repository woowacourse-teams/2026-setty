export const deliveryStatuses = ['REQUESTED', 'PICKED_UP', 'IN_DELIVERY', 'DELIVERED'] as const;

export type DeliveryStatus = (typeof deliveryStatuses)[number];

export type Order = {
    id: number;
    listingId: number;
    buyerId: number;
    deliveryStatus: DeliveryStatus;
};

export type MyOrder = {
    id: number;
    listing: {
        id: number;
        name: string;
        price: number;
        deliveryFee: number;
    };
    deliveryStatus: DeliveryStatus;
};

export type OrderErrorCode = 'ALREADY_ORDERED' | 'INVALID_TOKEN' | 'LISTING_NOT_FOUND' | 'CANNOT_ORDER_OWN_LISTING' | 'INVALID_REQUEST';

type ErrorResponse = {
    code: OrderErrorCode;
    message: string;
};

export class OrderApiError extends Error {
    readonly code: OrderErrorCode;

    constructor({ code, message }: ErrorResponse) {
        super(message);
        this.name = 'OrderApiError';
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
        if (isErrorResponse(body)) throw new OrderApiError(body);
    } catch (error) {
        if (error instanceof OrderApiError) throw error;
    }

    throw new Error(fallbackMessage);
}

export async function createOrder(listingId: number): Promise<Order> {
    const response = await fetch('/api/orders', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            ...authorizationHeaders()
        },
        body: JSON.stringify({ listingId })
    });
    await ensureSuccess(response, '주문 요청을 완료하지 못했습니다.');
    return response.json() as Promise<Order>;
}

export async function fetchMyOrders(): Promise<MyOrder[]> {
    const response = await fetch('/api/me/orders', { headers: authorizationHeaders() });
    await ensureSuccess(response, '내 주문 목록을 불러오지 못했습니다. 로그인 상태를 확인해 주세요.');
    return response.json() as Promise<MyOrder[]>;
}
