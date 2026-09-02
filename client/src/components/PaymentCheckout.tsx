import { ANONYMOUS, loadTossPayments, type TossPaymentsWidgets } from '@tosspayments/tosspayments-sdk';
import { useEffect, useRef, useState } from 'react';
import { createOrder } from '../api/orders';
import { TOSS_CLIENT_KEY, mockReturn, paymentReturnUrl } from '../payment/tossPayment';

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

        try {
            // 결제 전에 PENDING 주문을 만들고, 그 내부 주문 id를 토스 orderId로 쓴다(서버가 이 id로 조회·승인).
            const order = await createOrder(listingId);

            if (__ENABLE_MSW__) {
                mockReturn(order.id);
                return;
            }

            await widgetsRef.current?.requestPayment({
                orderId: String(order.id),
                orderName,
                successUrl: paymentReturnUrl('success'),
                failUrl: paymentReturnUrl('fail')
            });
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : '결제를 시작하지 못했습니다.');
            setRequesting(false);
        }
    };

    return (
        <div className="payment-checkout-backdrop" role="dialog" aria-modal="true" aria-label="결제">
            <div className="payment-checkout">
                <button className="payment-checkout__close" onClick={onClose} type="button" aria-label="결제 닫기">✕</button>
                <h2 className="payment-checkout__title">{orderName}</h2>
                <p className="payment-checkout__amount">{amount.toLocaleString('ko-KR')}원</p>

                {__ENABLE_MSW__ ? (
                    <p className="payment-checkout__mock-note">목 모드: 실제 결제창 없이 결제 성공을 흉내냅니다.</p>
                ) : (
                    <>
                        <div id="payment-method" />
                        <div id="agreement" />
                    </>
                )}

                {error && <p className="payment-checkout__error" role="alert">{error}</p>}

                <button
                    className="payment-checkout__pay"
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
