import { ANONYMOUS, loadTossPayments, type TossPaymentsWidgets } from '@tosspayments/tosspayments-sdk';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router';
import { trackEvent } from '../../analytics/posthog';
import { ApiError, NetworkError } from '../../api/http';
import { furnitureRepository, paymentRepository } from '../../api/repositories';
import { formatWon } from '../../model/furniture';
import { useNavigateBack } from '../../navigation';
import {
    BackIconButton,
    BottomBar,
    Button,
    CenterFill,
    LoadingIndicator,
    Scaffold,
    SettyMessage,
    TopAppBar
} from '../../ui/components';
import { showSnackbar } from '../../ui/snackbar';
import {
    PAY_PROCESS_CANCELED,
    readPendingPayment,
    savePendingPayment,
    tossOrderIdOf
} from './pendingPayment';

type PaymentStep =
    // 결제를 시작할 수 있는 상태
    | 'IDLE'
    // PENDING 주문 생성 중
    | 'CREATING_ORDER'
    // 토스 결제창으로 넘어가는 중
    | 'REQUESTING';

type PaymentLocationState = {
    orderName?: string;
    amount?: number;
    // 결제창에서 실패하고 돌아왔을 때 보여줄 문구
    failedMessage?: string | null;
};

const ALREADY_ORDERED = 'ALREADY_ORDERED';
// 서버는 PENDING 주문을 6분 뒤 만료시키고 1분 주기로 정리한다
const ALREADY_ORDERED_NOTICE = '다른 구매자가 결제 중인 매물입니다. 결제가 완료되지 않으면 7분 이내에 다시 구매할 수 있습니다';

function orderErrorMessage(error: unknown): string {
    if (error instanceof ApiError) return error.message;
    if (error instanceof NetworkError) return '네트워크 에러가 발생했습니다';
    return '주문을 만들지 못했습니다';
}

function paymentReturnUrl(result: 'success' | 'fail', listingId: number, tossOrderId: string): string {
    const url = new URL(`/payment/${result}`, window.location.origin);
    url.searchParams.set('listingId', String(listingId));
    // 구매자가 인증 전에 결제창을 닫으면 토스가 orderId를 빼고 돌려보낸다
    if (result === 'fail') url.searchParams.set('fallbackOrderId', tossOrderId);
    return url.toString();
}

function errorCodeOf(reason: unknown): string {
    return typeof reason === 'object' && reason !== null && 'code' in reason && typeof reason.code === 'string'
        ? reason.code
        : PAY_PROCESS_CANCELED;
}

/** 토스 결제위젯으로 매물을 결제하는 화면. 결제 수단과 약관 영역은 토스 결제위젯이 그린다. */
export function PaymentScreen() {
    const [searchParams] = useSearchParams();
    const listingId = Number(searchParams.get('listingId'));
    const location = useLocation();
    const navigate = useNavigate();
    const navigateBack = useNavigateBack();
    const locationState = (location.state ?? {}) as PaymentLocationState;
    const [order, setOrder] = useState<{ orderName: string; amount: number } | null>(
        locationState.orderName && locationState.amount
            ? { orderName: locationState.orderName, amount: locationState.amount }
            : null
    );
    const [loadError, setLoadError] = useState<string | null>(null);

    // 새로고침 등으로 매물 정보 없이 들어오면 매물 상세에서 이름과 총액을 다시 받는다
    useEffect(() => {
        if (order || !Number.isSafeInteger(listingId) || listingId <= 0) return;
        furnitureRepository.getFurnitureDetail(listingId)
            .then((listing) => {
                const pending = readPendingPayment();
                const amount = pending?.listingId === listingId ? pending.amount : listing.totalPrice;
                setOrder({ orderName: listing.title, amount });
            })
            .catch((error: unknown) => setLoadError(orderErrorMessage(error)));
    }, [order, listingId]);

    useEffect(() => {
        if (!locationState.failedMessage) return;
        showSnackbar(locationState.failedMessage);
        navigate(`${location.pathname}${location.search}`, { replace: true, state: { ...locationState, failedMessage: null } });
    }, [locationState, location.pathname, location.search, navigate]);

    if (!Number.isSafeInteger(listingId) || listingId <= 0 || loadError) {
        return (
            <PaymentScaffold canLeave onBack={navigateBack}>
                <CenterFill><SettyMessage shape="softBurst" shapeColor="var(--error-container)" title={loadError ?? '결제할 매물을 찾지 못했습니다'} /></CenterFill>
            </PaymentScaffold>
        );
    }
    if (!order) {
        return (
            <PaymentScaffold canLeave onBack={navigateBack}>
                <CenterFill><LoadingIndicator /></CenterFill>
            </PaymentScaffold>
        );
    }
    return <PaymentContent amount={order.amount} listingId={listingId} onBack={navigateBack} orderName={order.orderName} />;
}

type PaymentContentProps = {
    listingId: number;
    orderName: string;
    amount: number;
    onBack: () => void;
};

function PaymentContent({ listingId, orderName, amount: initialAmount, onBack }: PaymentContentProps) {
    // 결제할 금액. 주문을 만들기 전에는 매물 상세의 총액, 만든 뒤에는 주문 생성 응답의 총액이다
    const [amount, setAmount] = useState(initialAmount);
    const [isWidgetLoaded, setIsWidgetLoaded] = useState(__ENABLE_MSW__);
    // 약관 위젯이 아직 동의 상태를 알려주지 않았으면 null
    const [isAgreed, setIsAgreed] = useState<boolean | null>(null);
    const [step, setStep] = useState<PaymentStep>('IDLE');
    const widgetsRef = useRef<TossPaymentsWidgets | null>(null);
    const paymentRequested = useRef(false);
    const navigate = useNavigate();
    const canPay = isWidgetLoaded && isAgreed !== false && step === 'IDLE';

    useEffect(() => {
        trackEvent('checkout_opened', { listing_id: listingId, amount: initialAmount });
        return () => {
            // 뒤로 가기 등 앱 안에서 결제 화면을 떠난 경우만 기록한다. 토스 인증 이동은 이탈로 세지 않는다.
            if (!paymentRequested.current) trackEvent('checkout_closed', { listing_id: listingId });
        };
    }, [listingId, initialAmount]);

    useEffect(() => {
        if (__ENABLE_MSW__) return;
        let isActive = true;
        void (async () => {
            try {
                const tossPayments = await loadTossPayments(__TOSS_CLIENT_KEY__);
                // 웹 결제와 같이 비회원 결제로 연동한다
                const widgets = tossPayments.widgets({ customerKey: ANONYMOUS });
                await widgets.setAmount({ currency: 'KRW', value: initialAmount });
                const [, agreement] = await Promise.all([
                    widgets.renderPaymentMethods({ selector: '#payment-method', variantKey: 'DEFAULT' }),
                    widgets.renderAgreement({ selector: '#agreement', variantKey: 'AGREEMENT' })
                ]);
                if (!isActive) return;
                agreement.on('agreementStatusChange', (status) => setIsAgreed(status.agreedRequiredTerms));
                widgetsRef.current = widgets;
                setIsWidgetLoaded(true);
            } catch (reason) {
                if (isActive) showSnackbar(reason instanceof Error ? reason.message : '결제 수단을 불러오지 못했습니다');
            }
        })();
        return () => {
            isActive = false;
        };
    }, [initialAmount]);

    const startPayment = async () => {
        if (!canPay) return;
        setStep('CREATING_ORDER');
        let tossOrderId: string;
        let totalPrice: number;
        try {
            const pendingOrder = await paymentRepository.createOrder(listingId);
            tossOrderId = tossOrderIdOf(pendingOrder.id);
            totalPrice = pendingOrder.totalPrice;
        } catch (error) {
            setStep('IDLE');
            // 다른 구매자의 PENDING 주문이 매물을 선점 중이면 만료 후 다시 구매할 수 있음을 안내한다. 내 PENDING 주문은 서버가 그대로 돌려준다
            if (error instanceof ApiError && error.code === ALREADY_ORDERED) {
                showSnackbar(ALREADY_ORDERED_NOTICE, { long: true });
            } else {
                showSnackbar(orderErrorMessage(error));
            }
            return;
        }

        setAmount(totalPrice);
        setStep('REQUESTING');
        savePendingPayment({ tossOrderId, amount: totalPrice, listingId });
        paymentRequested.current = true;

        if (__ENABLE_MSW__) {
            // 목 모드: 토스 결제창 없이 결제 성공으로 돌아온 것처럼 이동한다
            const params = new URLSearchParams({ listingId: String(listingId), paymentKey: `mock_${Date.now()}`, orderId: tossOrderId, amount: String(totalPrice) });
            navigate(`/payment/success?${params.toString()}`, { replace: true });
            return;
        }

        try {
            const widgets = widgetsRef.current;
            if (!widgets) throw new Error('결제 수단을 불러오지 못했습니다');
            // 위젯은 매물 상세의 총액으로 렌더링됐으므로 서버가 검증할 주문 총액으로 맞춘 뒤 결제창을 연다
            await widgets.setAmount({ currency: 'KRW', value: totalPrice });
            await widgets.requestPayment({
                orderId: tossOrderId,
                orderName,
                windowTarget: 'self',
                successUrl: paymentReturnUrl('success', listingId, tossOrderId),
                failUrl: paymentReturnUrl('fail', listingId, tossOrderId)
            });
        } catch (reason) {
            // 결제창이 failUrl로 돌아가지 않고 요청을 거절한 경우. 이미 만든 주문은 정리한다
            void paymentRepository.fail(tossOrderId).catch(() => undefined);
            trackEvent('payment_failed', { order_id: tossOrderId.split('_')[0], code: errorCodeOf(reason) });
            paymentRequested.current = false;
            setStep('IDLE');
            // 사용자가 결제창을 직접 닫은 경우는 에러로 보여주지 않는다
            if (errorCodeOf(reason) !== PAY_PROCESS_CANCELED) {
                showSnackbar(reason instanceof Error ? reason.message : '결제를 진행하지 못했습니다');
            }
        }
    };

    return (
        <PaymentScaffold
            bottomBar={(
                <Button disabled={!canPay} fullWidth onClick={() => void startPayment()} size="medium">
                    {step === 'IDLE' ? `${formatWon(amount)} 결제하기` : '결제 진행 중'}
                </Button>
            )}
            canLeave
            onBack={onBack}
        >
            {__ENABLE_MSW__ ? (
                <p className="type-body-medium" style={{ margin: 0, padding: '16px 24px', color: 'var(--on-surface-variant)' }}>
                    목 모드: 토스 결제위젯 없이 결제 성공을 흉내냅니다.
                </p>
            ) : (
                <>
                    <div id="payment-method" />
                    <div id="agreement" />
                    {!isWidgetLoaded && <CenterFill><LoadingIndicator /></CenterFill>}
                </>
            )}
        </PaymentScaffold>
    );
}

type PaymentScaffoldProps = {
    canLeave: boolean;
    onBack: () => void;
    bottomBar?: ReactNode;
    children: ReactNode;
};

export function PaymentScaffold({ canLeave, onBack, bottomBar, children }: PaymentScaffoldProps) {
    return (
        <Scaffold
            bottomBar={bottomBar && <BottomBar>{bottomBar}</BottomBar>}
            topBar={(
                <TopAppBar
                    navigation={<BackIconButton disabled={!canLeave} onBack={onBack} />}
                    title={<span className="type-title-large-em" style={{ fontWeight: 400 }}>결제</span>}
                />
            )}
        >
            {children}
        </Scaffold>
    );
}
