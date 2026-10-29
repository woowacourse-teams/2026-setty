import { useCallback, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { isUnauthorized, toUserMessage } from '../../api/errors';
import { orderRepository } from '../../api/repositories';
import { formatWon } from '../../model/furniture';
import {
    canCancel,
    deliveryProgress,
    deliveryStatusLabel,
    deliverySteps,
    isCancelFlow,
    isDeliveryInProgress,
    orderedTotalPrice,
    orderStatusDescription,
    orderStatusLabel,
    type DeliveryStatus,
    type Order,
    type OrderedFurniture
} from '../../model/order';
import { routes, useNavigateBack, useNavigateToLogin } from '../../navigation';
import {
    AlertDialog,
    BackIconButton,
    BottomBar,
    Button,
    ErrorContent,
    FlexibleTopAppBar,
    FurnitureThumbnail,
    LoadingContent,
    LoadingIndicator,
    PullToRefresh,
    Scaffold,
    SectionTitle,
    SegmentedList,
    SegmentedListItem,
    WavyProgress
} from '../../ui/components';
import { Icon } from '../../ui/Icon';
import { shapeStyle } from '../../ui/shapes';
import { showSnackbar } from '../../ui/snackbar';
import { useLoad } from '../useLoad';
import { usePolling } from '../usePolling';
import screenStyles from '../screens.module.css';

const CANCEL_POLL_COUNT = 3;
const CANCEL_POLL_DELAY_MILLIS = 1_500;

export function OrderDetailScreen() {
    const { orderId: orderIdParam } = useParams();
    const orderId = Number(orderIdParam);
    const navigate = useNavigate();
    const navigateBack = useNavigateBack();
    const navigateToLogin = useNavigateToLogin();
    const fetchOrder = useCallback(() => orderRepository.getOrder(orderId), [orderId]);
    const { state, isRefreshing, load, refresh, fetch } = useLoad(fetchOrder);
    const [isCancelDialogVisible, setIsCancelDialogVisible] = useState(false);
    const [isCancelling, setIsCancelling] = useState(false);
    const order = state.status === 'success' ? state.data : null;
    // 배송원이 배차를 진행하면 새로고침하지 않아도 진행 단계가 바뀌도록 조용히 다시 불러온다
    usePolling(() => void fetch(), order !== null && isDeliveryInProgress(order) && !isCancelling);

    /**
     * 취소를 요청한다. 202 응답은 접수 결과일 뿐이라 주문 상세를 다시 조회해 최종 상태를 확인한다.
     * 배송이 취소를 처리하는 동안은 CANCEL_PENDING이므로 몇 번 더 조회한다.
     */
    const cancel = async () => {
        if (isCancelling) return;
        setIsCancelling(true);
        try {
            await orderRepository.cancel(orderId);
            showSnackbar('취소 요청을 접수했어요');
            for (let poll = 0; poll < CANCEL_POLL_COUNT; poll += 1) {
                const latest = await fetch();
                if (latest?.orderStatus !== 'CANCEL_PENDING') break;
                await new Promise((resolve) => setTimeout(resolve, CANCEL_POLL_DELAY_MILLIS));
            }
        } catch (error) {
            if (isUnauthorized(error)) {
                navigateToLogin();
            } else {
                showSnackbar(toUserMessage(error));
            }
        }
        setIsCancelling(false);
    };

    return (
        <Scaffold
            bottomBar={order && (canCancel(order) || isCancelling) && (
                <BottomBar>
                    {isCancelling ? <LoadingIndicator /> : (
                        <Button fullWidth onClick={() => setIsCancelDialogVisible(true)} size="medium" variant="danger-tonal">주문 취소하기</Button>
                    )}
                </BottomBar>
            )}
            topBar={(
                <FlexibleTopAppBar
                    navigation={<BackIconButton onBack={navigateBack} />}
                    subtitle={`주문 #${orderId}`}
                    title="주문 상세"
                />
            )}
        >
            {state.status === 'loading' && <LoadingContent />}
            {state.status === 'error' && (
                <ErrorContent isUnauthorized={state.isUnauthorized} message={state.message} onRequireLogin={navigateToLogin} onRetry={load} />
            )}
            {order && (
                <PullToRefresh isRefreshing={isRefreshing} onRefresh={refresh}>
                    <div className={screenStyles['list-padding']} style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
                        <OrderStatusCard order={order} />
                        {order.listing && (
                            <>
                                <OrderedFurnitureSection listing={order.listing} onClick={() => navigate(routes.furnitureDetail(order.listing!.id))} />
                                <PaymentSection listing={order.listing} />
                            </>
                        )}
                    </div>
                </PullToRefresh>
            )}
            {isCancelDialogVisible && (
                <AlertDialog
                    confirmLabel="주문 취소"
                    dismissLabel="닫기"
                    onConfirm={() => {
                        setIsCancelDialogVisible(false);
                        void cancel();
                    }}
                    onDismiss={() => setIsCancelDialogVisible(false)}
                    text="배송 기사가 이미 배정됐다면 취소가 거절될 수 있어요."
                    title="주문을 취소할까요?"
                />
            )}
        </Scaffold>
    );
}

function OrderStatusCard({ order }: { order: Order }) {
    const isCancelled = isCancelFlow(order);
    return (
        <section
            className={screenStyles.card}
            style={{
                background: isCancelled ? 'var(--error-container)' : 'var(--primary-container)',
                color: isCancelled ? 'var(--on-error-container)' : 'var(--on-primary-container)'
            }}
        >
            <div className={screenStyles.card__header}>
                <div
                    className={screenStyles.card__shape}
                    style={{
                        ...shapeStyle(isCancelled ? 'softBurst' : 'cookie9'),
                        background: isCancelled ? 'var(--error)' : 'var(--primary)',
                        color: isCancelled ? 'var(--on-error)' : 'var(--on-primary)'
                    }}
                >
                    <Icon name="localShipping" size={28} />
                </div>
                <div className={screenStyles.card__lines}>
                    <p className="type-headline-small-em">{orderStatusLabel(order)}</p>
                    <p className="type-body-medium-em">{orderStatusDescription(order)}</p>
                </div>
            </div>
            {!isCancelled && order.orderStatus !== 'PENDING' && <DeliveryProgress deliveryStatus={order.deliveryStatus} />}
        </section>
    );
}

function DeliveryProgress({ deliveryStatus }: { deliveryStatus: DeliveryStatus }) {
    const currentIndex = deliverySteps.indexOf(deliveryStatus);
    return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <WavyProgress progress={deliveryProgress(deliveryStatus)} />
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                {deliverySteps.map((step, index) => (
                    <span
                        className="type-label-medium"
                        key={step}
                        style={{
                            fontWeight: step === deliveryStatus ? 900 : 400,
                            opacity: index <= currentIndex ? 1 : 0.5
                        }}
                    >
                        {deliveryStatusLabel(step)}
                    </span>
                ))}
            </div>
        </div>
    );
}

function OrderedFurnitureSection({ listing, onClick }: { listing: OrderedFurniture; onClick: () => void }) {
    return (
        <section style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <SectionTitle>주문한 가구</SectionTitle>
            <SegmentedList>
                <SegmentedListItem
                    count={1}
                    headline={<span className="type-title-medium">{listing.name}</span>}
                    index={0}
                    leading={<FurnitureThumbnail alt={listing.name} imageUrl={listing.thumbnailUrl} placeholder="가구" />}
                    onClick={onClick}
                    trailing={<Icon name="chevronRight" />}
                />
            </SegmentedList>
        </section>
    );
}

function PaymentSection({ listing }: { listing: OrderedFurniture }) {
    const rows: Array<[string, number]> = [
        ['매물 가격', listing.price],
        ['배송비', listing.deliveryFee]
    ];
    return (
        <section style={{ display: 'flex', flexDirection: 'column' }}>
            <SectionTitle>결제 정보</SectionTitle>
            <SegmentedList>
                {rows.map(([label, amount], index) => (
                    <SegmentedListItem
                        count={rows.length + 1}
                        headline={<span className="type-body-large-em" style={{ color: 'var(--on-surface-variant)' }}>{label}</span>}
                        index={index}
                        key={label}
                        trailing={<span className="type-body-large-em" style={{ color: 'var(--on-surface)' }}>{formatWon(amount)}</span>}
                    />
                ))}
                <li className={screenStyles['total-row']} style={{ listStyle: 'none' }}>
                    <span className="type-title-medium-em">총 결제 금액</span>
                    <span className={`${screenStyles['total-row__amount']} type-headline-small-em`}>{formatWon(orderedTotalPrice(listing))}</span>
                </li>
            </SegmentedList>
        </section>
    );
}
