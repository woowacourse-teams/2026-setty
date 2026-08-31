// 토스페이먼츠 테스트 클라이언트 키(공개값). 실제 결제는 붙이지 않는다.
// 실모드로 결제창을 띄우려면 팀의 테스트 클라이언트 키로 교체한다. 목 모드에서는 사용되지 않는다.
const TOSS_CLIENT_KEY = 'test_ck_replace_with_team_test_client_key';

const PENDING_PAYMENT_KEY = 'setty:pending-payment';

export type PendingPayment = {
    tossOrderId: string;
    listingId: number;
    amount: number;
};

export type StartPaymentParams = {
    listingId: number;
    amount: number;
    orderName: string;
};

/**
 * 결제를 시작한다. 성공/실패 모두 successUrl·failUrl로 페이지가 이동하므로,
 * 이 함수가 정상 반환되기 전에 브라우저가 이동할 수 있다. 실제 승인은 복귀 후 confirm에서 이뤄진다.
 *
 * - 실모드: 토스 SDK 결제창을 띄운다.
 * - 목 모드(__ENABLE_MSW__): 토스로 나가지 않고 successUrl로 즉시 이동해 성공 흐름을 흉내낸다.
 */
export async function startPayment(params: StartPaymentParams): Promise<void> {
    const tossOrderId = crypto.randomUUID();
    savePending({ tossOrderId, listingId: params.listingId, amount: params.amount });

    const successUrl = paymentReturnUrl('success');
    const failUrl = paymentReturnUrl('fail');

    if (__ENABLE_MSW__) {
        const mockSuccess = new URL(successUrl);
        mockSuccess.searchParams.set('paymentKey', `mock_pk_${tossOrderId}`);
        mockSuccess.searchParams.set('orderId', tossOrderId);
        mockSuccess.searchParams.set('amount', String(params.amount));
        window.location.assign(mockSuccess.toString());
        return;
    }

    const { loadTossPayments, ANONYMOUS } = await import('@tosspayments/tosspayments-sdk');
    const tossPayments = await loadTossPayments(TOSS_CLIENT_KEY);
    const payment = tossPayments.payment({ customerKey: ANONYMOUS });
    await payment.requestPayment({
        method: 'CARD',
        amount: { currency: 'KRW', value: params.amount },
        orderId: tossOrderId,
        orderName: params.orderName,
        successUrl,
        failUrl,
        card: {
            useEscrow: false,
            flowMode: 'DEFAULT',
            useCardPoint: false,
            useAppCardOnly: false
        }
    });
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

function savePending(pending: PendingPayment) {
    window.sessionStorage.setItem(PENDING_PAYMENT_KEY, JSON.stringify(pending));
}

function paymentReturnUrl(result: 'success' | 'fail'): string {
    const url = new URL(window.location.href);
    url.search = '';
    url.hash = '';
    url.searchParams.set('payment', result);
    return url.toString();
}
