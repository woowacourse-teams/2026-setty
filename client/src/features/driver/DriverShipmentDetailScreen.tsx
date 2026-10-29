import { useCallback, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { deliveryRepository } from '../../api/deliveryRepository';
import {
    categoryText,
    formatTime,
    shipmentProgress,
    shipmentStatusLabel,
    shipmentSteps,
    type ShipmentDetail
} from '../../model/delivery';
import { formatWon } from '../../model/furniture';
import { routes, useNavigateBack, useNavigateToDriverLogin } from '../../navigation';
import {
    AlertDialog,
    BackIconButton,
    BottomBar,
    Button,
    ErrorContent,
    FlexibleTopAppBar,
    LoadingContent,
    LoadingIndicator,
    PullToRefresh,
    Scaffold,
    SectionTitle,
    SegmentedList,
    SegmentedListItem,
    WavyProgress
} from '../../ui/components';
import { showSnackbar } from '../../ui/snackbar';
import { useLoad } from '../useLoad';
import screenStyles from '../screens.module.css';
import { RoutePoint, ShipmentStatusBadge, useDriverErrorHandler } from './DeliveryParts';
import styles from './driver.module.css';

/** 내가 수락한 배차. 픽업 → 배송 완료 순서로 진행하고, 픽업 전에는 취소할 수 있다. */
export function DriverShipmentDetailScreen() {
    const deliveryId = Number(useParams().deliveryId);
    const navigate = useNavigate();
    const navigateBack = useNavigateBack(routes.driverShipments);
    const navigateToDriverLogin = useNavigateToDriverLogin();
    const handleError = useDriverErrorHandler();
    const fetchShipment = useCallback(() => deliveryRepository.getShipment(deliveryId), [deliveryId]);
    const { state, isRefreshing, load, refresh, fetch } = useLoad(fetchShipment, 'driver');
    const [isWorking, setIsWorking] = useState(false);
    const [isCancelDialogVisible, setIsCancelDialogVisible] = useState(false);
    const shipment = state.status === 'success' ? state.data : null;

    const run = async (action: () => Promise<void>, message: string) => {
        if (isWorking) return;
        setIsWorking(true);
        try {
            await action();
            showSnackbar(message);
            await fetch();
        } catch (error) {
            handleError(error);
            await fetch();
        }
        setIsWorking(false);
    };

    const cancel = async () => {
        if (isWorking) return;
        setIsWorking(true);
        try {
            await deliveryRepository.cancel(deliveryId);
            navigate(routes.driverShipments, { replace: true });
            showSnackbar('배차를 취소했어요. 요청이 다른 배송원에게 다시 열려요.', { keepOnNextScreen: true });
        } catch (error) {
            setIsWorking(false);
            handleError(error);
        }
    };

    return (
        <Scaffold
            bottomBar={shipment && shipment.status !== 'DELIVERED' && (
                <BottomBar>
                    {isWorking ? <LoadingIndicator /> : (
                        <div className={styles.actions}>
                            {shipment.status === 'ACCEPTED' && (
                                <>
                                    <Button onClick={() => setIsCancelDialogVisible(true)} size="medium" variant="danger-tonal">배차 취소</Button>
                                    <Button onClick={() => void run(() => deliveryRepository.pickUp(deliveryId), '가구를 받았어요. 구매자에게 배송해 주세요.')} size="medium">
                                        픽업 완료
                                    </Button>
                                </>
                            )}
                            {shipment.status === 'PICKED_UP' && (
                                <Button onClick={() => void run(() => deliveryRepository.complete(deliveryId), '배송을 완료했어요. 수고하셨어요!')} size="medium">
                                    배송 완료
                                </Button>
                            )}
                        </div>
                    )}
                </BottomBar>
            )}
            topBar={(
                <FlexibleTopAppBar
                    navigation={<BackIconButton onBack={navigateBack} />}
                    subtitle={shipment ? `주문 #${shipment.orderId}` : undefined}
                    title="배차 상세"
                />
            )}
        >
            {state.status === 'loading' && <LoadingContent />}
            {state.status === 'error' && (
                <ErrorContent isUnauthorized={state.isUnauthorized} message={state.message} onRequireLogin={navigateToDriverLogin} onRetry={load} />
            )}
            {shipment && (
                <PullToRefresh isRefreshing={isRefreshing} onRefresh={refresh}>
                    <div className={`${screenStyles['list-padding']} ${styles.stack}`}>
                        <ShipmentStatusCard shipment={shipment} />
                        <section className={styles.section}>
                            <SectionTitle>배송 경로</SectionTitle>
                            <SegmentedList>
                                <SegmentedListItem
                                    count={2}
                                    headline={<RoutePoint address={shipment.pickup.address} label="출발지 · 판매자" phoneNumber={shipment.pickup.phoneNumber} />}
                                    index={0}
                                />
                                <SegmentedListItem
                                    count={2}
                                    headline={<RoutePoint address={shipment.destination.address} label="도착지 · 구매자" phoneNumber={shipment.destination.phoneNumber} />}
                                    index={1}
                                />
                            </SegmentedList>
                        </section>
                    </div>
                </PullToRefresh>
            )}
            {isCancelDialogVisible && (
                <AlertDialog
                    confirmLabel="배차 취소"
                    dismissLabel="닫기"
                    onConfirm={() => {
                        setIsCancelDialogVisible(false);
                        void cancel();
                    }}
                    onDismiss={() => setIsCancelDialogVisible(false)}
                    text="요청이 다른 배송원에게 다시 열려요."
                    title="배차를 취소할까요?"
                />
            )}
        </Scaffold>
    );
}

function ShipmentStatusCard({ shipment }: { shipment: ShipmentDetail }) {
    const timeOf = { ACCEPTED: shipment.acceptedAt, PICKED_UP: shipment.pickedUpAt, DELIVERED: shipment.deliveredAt, REQUESTED: shipment.requestedAt };
    const currentIndex = shipmentSteps.indexOf(shipment.status);
    return (
        <section className={screenStyles.card} style={{ background: 'var(--primary-container)', color: 'var(--on-primary-container)' }}>
            <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 12 }}>
                <div className={styles.hero}>
                    <p className="type-label-large">{categoryText(shipment.furniture.category)}</p>
                    <p className="type-headline-small-em">{shipment.furniture.itemName}</p>
                    <p className="type-title-medium-em">{formatWon(shipment.deliveryFee)}</p>
                </div>
                <ShipmentStatusBadge status={shipment.status} />
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                <WavyProgress progress={shipmentProgress(shipment.status)} />
                <div className={styles['step-row']}>
                    {shipmentSteps.map((step, index) => (
                        <span className={styles.step} key={step} style={{ opacity: index <= currentIndex ? 1 : 0.5 }}>
                            <span className="type-label-medium" style={{ fontWeight: step === shipment.status ? 900 : 400 }}>
                                {shipmentStatusLabel(step)}
                            </span>
                            <span className="type-label-medium">{formatTime(timeOf[step]) || ' '}</span>
                        </span>
                    ))}
                </div>
            </div>
        </section>
    );
}
