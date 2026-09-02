import { ANONYMOUS, loadTossPayments, type TossPaymentsWidgets } from '@tosspayments/tosspayments-sdk';
import { useEffect, useRef, useState } from 'react';
import { createOrder } from '../api/orders';
import { TOSS_CLIENT_KEY, mockReturn, paymentReturnUrl } from '../payment/tossPayment';
import paymentStyles from '../styles/modules/Payment.module.css';

type PaymentCheckoutProps = {
    listingId: number;
    amount: number;
    orderName: string;
    onClose: () => void;
};

export function PaymentCheckout({ listingId, amount, orderName, onClose }: PaymentCheckoutProps) {
    const [ready, setReady] = useState(__ENABLE_MSW__);
    const [requesting, setRequesting] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const widgetsRef = useRef<TossPaymentsWidgets | null>(null);

    useEffect(() => {
        if (__ENABLE_MSW__) return;

        let active = true;
        void (async () => {
            try {
                const tossPayments = await loadTossPayments(TOSS_CLIENT_KEY);
                const widgets = tossPayments.widgets({ customerKey: ANONYMOUS });
                await widgets.setAmount({ currency: 'KRW', value: amount });
                await Promise.all([
                    widgets.renderPaymentMethods({ selector: '#payment-method', variantKey: 'DEFAULT' }),
                    widgets.renderAgreement({ selector: '#agreement', variantKey: 'AGREEMENT' })
                ]);
                if (!active) return;
                widgetsRef.current = widgets;
                setReady(true);
            } catch (reason) {
                if (active) setError(reason instanceof Error ? reason.message : '결제 위젯을 불러오지 못했습니다.');
            }
        })();

        return () => { active = false; };
    }, [amount]);

    const requestPayment = async () => {
        setRequesting(true);
        setError(null);

        // 데스크톱 오버레이 결제창은 취소 시 failUrl로 리다이렉트하지 않고 requestPayment를 reject한다.
        // 주문 생성 이후의 취소·실패는 서버 실패 복귀로 보내 PENDING 주문 정리(선점 해제)를 태운다.
        let tossOrderId: string | null = null;
        try {
            // 결제 전에 PENDING 주문을 만들고, 그 내부 주문 id를 토스 orderId로 쓴다(서버가 이 id로 조회·승인).
            const order = await createOrder(listingId);
            // 토스 orderId는 6~64자이며 매번 유일해야 한다(승인/취소된 값은 재사용 불가).
            // `<내부 주문id>_<랜덤>` 복합키로 만들어 매 시도 유일하게 하고, 서버는 앞부분에서 주문 id를 추출한다.
            tossOrderId = `${order.id}_${crypto.randomUUID()}`;

            if (__ENABLE_MSW__) {
                mockReturn(order.id);
                return;
            }

            await widgetsRef.current?.requestPayment({
                orderId: tossOrderId,
                orderName,
                successUrl: paymentReturnUrl('success'),
                failUrl: paymentReturnUrl('fail')
            });
        } catch (reason) {
            if (tossOrderId) {
                const code = typeof reason === 'object' && reason !== null && 'code' in reason && typeof reason.code === 'string'
                    ? reason.code
                    : 'PAY_PROCESS_CANCELED';
                window.location.assign(`${paymentReturnUrl('fail')}?orderId=${tossOrderId}&code=${encodeURIComponent(code)}`);
                return;
            }
            setError(reason instanceof Error ? reason.message : '결제를 시작하지 못했습니다.');
            setRequesting(false);
        }
    };

    return (
        <div className={paymentStyles['payment-checkout-backdrop']} role="dialog" aria-modal="true" aria-label="결제">
            <div className={paymentStyles['payment-checkout']}>
                <button className={paymentStyles['payment-checkout__close']} onClick={onClose} type="button" aria-label="결제 닫기">✕</button>
                <h2 className={paymentStyles['payment-checkout__title']}>{orderName}</h2>
                <p className={paymentStyles['payment-checkout__amount']}>{amount.toLocaleString('ko-KR')}원</p>

                {__ENABLE_MSW__ ? (
                    <p className={paymentStyles['payment-checkout__mock-note']}>목 모드: 실제 결제창 없이 결제 성공을 흉내냅니다.</p>
                ) : (
                    <>
                        <div id="payment-method" />
                        <div id="agreement" />
                    </>
                )}

                {error && <p className={paymentStyles['payment-checkout__error']} role="alert">{error}</p>}

                <button
                    className={paymentStyles['payment-checkout__pay']}
                    disabled={!ready || requesting}
                    onClick={() => void requestPayment()}
                    type="button"
                >
                    {requesting ? '결제 진행 중...' : `${amount.toLocaleString('ko-KR')}원 결제하기`}
                </button>
            </div>
        </div>
    );
}
