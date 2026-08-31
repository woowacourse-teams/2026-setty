// 토스페이먼츠 결제위젯 테스트 클라이언트 키(공개 docs 키). 실제 결제는 붙이지 않는다.
// 서버는 짝이 되는 위젯 시크릿 키(test_gsk_docs_...)로 승인을 호출한다. 목 모드에서는 사용되지 않는다.
export const TOSS_CLIENT_KEY = 'test_gck_docs_Ovk5rk1EwkEbP0W43n07xlzm';

const PENDING_PAYMENT_KEY = 'setty:pending-payment';

export type PendingPayment = {
    tossOrderId: string;
    listingId: number;
    amount: number;
};

export function savePending(pending: PendingPayment) {
    window.sessionStorage.setItem(PENDING_PAYMENT_KEY, JSON.stringify(pending));
}

export function readPending(): PendingPayment | null {
    const raw = window.sessionStorage.getItem(PENDING_PAYMENT_KEY);
    if (!raw) return null;
    try {
        const parsed = JSON.parse(raw) as PendingPayment;
        return typeof parsed.tossOrderId === 'string' && typeof parsed.listingId === 'number'
            ? parsed
            : null;
    } catch {
        return null;
    }
}

export function clearPending() {
    window.sessionStorage.removeItem(PENDING_PAYMENT_KEY);
}

/** 결제 성공/실패 시 돌아올 URL. 경로는 항상 현재 페이지, 쿼리로만 결과를 표시한다. */
export function paymentReturnUrl(result: 'success' | 'fail'): string {
    const url = new URL(window.location.href);
    url.search = '';
    url.hash = '';
    url.searchParams.set('payment', result);
    return url.toString();
}

/** 목 모드: 토스로 나가지 않고 successUrl로 즉시 이동해 성공 흐름을 흉내낸다. */
export function mockRedirect(successUrl: string, tossOrderId: string, amount: number) {
    const url = new URL(successUrl);
    url.searchParams.set('paymentKey', `mock_pk_${tossOrderId}`);
    url.searchParams.set('orderId', tossOrderId);
    url.searchParams.set('amount', String(amount));
    window.location.assign(url.toString());
}
