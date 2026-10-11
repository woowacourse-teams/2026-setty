import { useEffect, useRef, useState } from 'react';
import { useNavigate, type NavigateFunction } from 'react-router';
import { trackEvent } from '../analytics/posthog';

export type PaymentNotice = {
    tone: 'success' | 'error';
    message: string;
};

/**
 * 서버 결제 복귀(GET /payments/success·/fail) 처리 후 SPA로 302되어 왔을 때 한 번만 실행된다.
 * 승인·결제 저장·PaymentCompleted 발행은 이미 서버가 마쳤으므로, 여기서는 결과만 표시하고
 * 목록을 갱신한다(추가 승인 호출 없음). 처리 후 쿼리파라미터를 정리해 새로고침 중복 처리를 막는다.
 */
export function usePaymentReturn(onConfirmed: () => void) {
    const navigate = useNavigate();
    const [notice, setNotice] = useState<PaymentNotice | null>(null);
    const processed = useRef(false);

    useEffect(() => {
        if (processed.current) return;
        processed.current = true;

        const params = new URLSearchParams(window.location.search);
        const payment = params.get('payment');
        if (payment !== 'success' && payment !== 'fail') return;

        const orderId = params.get('orderId')?.split('_')[0];
        const properties: Record<string, string> = {};
        if (orderId) properties.order_id = orderId;
        const failureCode = params.get('reason') ?? params.get('code') ?? '';
        cleanPaymentQuery(navigate);

        if (payment === 'fail') {
            trackEvent('payment_failed', { ...properties, code: failureCode });
            setNotice({ tone: 'error', message: '결제가 취소되었거나 실패했습니다. 다시 시도해 주세요.' });
            return;
        }

        trackEvent('payment_succeeded', properties);
        setNotice({ tone: 'success', message: '결제가 완료되었습니다. 내 주문에서 배송 상태를 확인할 수 있습니다.' });
        onConfirmed();
    }, [navigate, onConfirmed]);

    return { notice, dismiss: () => setNotice(null) };
}

function cleanPaymentQuery(navigate: NavigateFunction) {
    const url = new URL(window.location.href);
    ['payment', 'paymentKey', 'orderId', 'amount', 'paymentType', 'reason', 'code', 'message'].forEach((key) => url.searchParams.delete(key));
    void navigate(`${url.pathname}${url.search}${url.hash}`, { replace: true });
}
