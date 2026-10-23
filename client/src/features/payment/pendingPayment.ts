const PENDING_PAYMENT_KEY = 'setty:pending-payment';

/** 토스 결제창으로 넘어가기 전에 남겨 두는 결제 요청. 결제창에서 돌아온 화면이 승인 금액과 돌아갈 매물을 찾는다. */
export type PendingPayment = {
    tossOrderId: string;
    // 주문 생성 응답의 총액. 서버 승인 요청에 이 금액을 보낸다
    amount: number;
    listingId: number;
};

export function savePendingPayment(payment: PendingPayment) {
    try {
        window.sessionStorage.setItem(PENDING_PAYMENT_KEY, JSON.stringify(payment));
    } catch {
        // 저장하지 못하면 결제창이 돌려준 금액으로 승인한다
    }
}

export function readPendingPayment(): PendingPayment | null {
    try {
        const value = window.sessionStorage.getItem(PENDING_PAYMENT_KEY);
        return value ? JSON.parse(value) as PendingPayment : null;
    } catch {
        return null;
    }
}

export function clearPendingPayment() {
    try {
        window.sessionStorage.removeItem(PENDING_PAYMENT_KEY);
    } catch {
        // 지울 수 없으면 다음 결제 요청이 덮어쓴다
    }
}

/** 토스 orderId는 매 시도마다 유일해야 하므로 `<주문id>_<랜덤>`으로 만든다. 서버는 앞부분에서 주문 id를 읽는다. */
export function tossOrderIdOf(orderId: number): string {
    return `${orderId}_${crypto.randomUUID()}`;
}

export const PAY_PROCESS_CANCELED = 'PAY_PROCESS_CANCELED';
