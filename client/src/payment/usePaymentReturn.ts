import { useEffect, useRef, useState } from 'react';
import { confirmPayment } from '../api/payments';
import { clearPending, readPending } from './tossPayment';

export type PaymentNotice = {
    tone: 'success' | 'error';
    message: string;
};

/**
 * 결제창에서 successUrl·failUrl로 복귀했을 때 한 번만 실행된다.
 * 성공이면 서버에 결제 승인을 요청하고, 완료 시 onConfirmed를 호출한다(내 주문으로 이동 등).
 * 처리 후 쿼리파라미터를 정리해 새로고침으로 중복 승인되지 않게 한다.
 */
export function usePaymentReturn(onConfirmed: () => void) {
    const [notice, setNotice] = useState<PaymentNotice | null>(null);
    const processed = useRef(false);

    useEffect(() => {
        if (processed.current) return;
        processed.current = true;

        const params = new URLSearchParams(window.location.search);
        const payment = params.get('payment');
        if (payment !== 'success' && payment !== 'fail') return;

        cleanPaymentQuery();

        if (payment === 'fail') {
            clearPending();
            setNotice({ tone: 'error', message: '결제가 취소되었거나 실패했습니다. 다시 시도해 주세요.' });
            return;
        }

        const paymentKey = params.get('paymentKey');
        const orderId = params.get('orderId');
        const amount = Number(params.get('amount'));
        const pending = readPending();

        if (!paymentKey || !orderId || !pending || pending.tossOrderId !== orderId || Number.isNaN(amount)) {
            clearPending();
            setNotice({ tone: 'error', message: '결제 정보를 확인하지 못했습니다.' });
            return;
        }

        void confirmPayment({ listingId: pending.listingId, tossOrderId: orderId, paymentKey, amount })
            .then(() => {
                clearPending();
                setNotice({ tone: 'success', message: '결제가 완료되었습니다. 내 주문에서 배송 상태를 확인할 수 있습니다.' });
                onConfirmed();
            })
            .catch((reason: unknown) => {
                clearPending();
                setNotice({ tone: 'error', message: reason instanceof Error ? reason.message : '결제 승인에 실패했습니다.' });
            });
        // 복귀 시 최초 1회만 처리한다. onConfirmed는 처리 시점 값을 사용한다.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    return { notice, dismiss: () => setNotice(null) };
}

function cleanPaymentQuery() {
    const url = new URL(window.location.href);
    ['payment', 'paymentKey', 'orderId', 'amount', 'code', 'message'].forEach((key) => url.searchParams.delete(key));
    window.history.replaceState({}, '', `${url.pathname}${url.search}${url.hash}`);
}
