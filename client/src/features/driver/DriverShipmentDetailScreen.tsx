import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { deliveryRepository } from '../../api/deliveryRepository';
import {
    categoryText,
    formatTime,
    shortAddress,
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
import { showSnackbar } from '../../ui/snackbar';
import { useLoad } from '../useLoad';
import screenStyles from '../screens.module.css';
import { ACTION_RESULT_MIN_MILLIS, withMinimumDuration } from './actionResult';
import { ActionResultScreen, type ActionResultContent } from './ActionResultScreen';
import { DeliveryDistance, RoutePoint, ShipmentStatusBadge, useDriverErrorHandler } from './DeliveryParts';
import styles from './driver.module.css';

const now = () => formatTime(new Date().toISOString());

function pickUpResult(shipment: ShipmentDetail): ActionResultContent {
    return {
        icon: 'localShipping',
        workingTitle: '픽업 처리 중이에요',
        title: '픽업 완료!',
        description: '이제 구매자에게 배송해 주세요.',
        rows: [
            { label: '픽업 시각', value: now() },
            { label: '다음 목적지', value: shortAddress(shipment.destination.address) }
        ]
    };
}

function completeResult(shipment: ShipmentDetail): ActionResultContent {
    return {
        icon: 'payments',
        workingTitle: '배송 완료 처리 중이에요',
        title: '배송 완료!',
        description: '이번 배송비',
        amount: shipment.deliveryFee,
        rows: [
            { label: '배송 완료 시각', value: now() },
            { label: '배송한 가구', value: shipment.furniture.itemName }
        ]
    };
}

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
    const [result, setResult] = useState<{ content: ActionResultContent; isDone: boolean } | null>(null);
    const shipment = state.status === 'success' ? state.data : null;

    // 응답이 빨라도 처리 중 화면을 잠깐 보여 준 뒤 결과를 띄운다. 실패하면 결과 화면 없이 오류만 알린다.
    const run = async (action: () => Promise<void>, buildResult: () => ActionResultContent) => {
        if (isWorking) return;
        setIsWorking(true);
        setResult({ content: buildResult(), isDone: false });
        try {
            await withMinimumDuration(action(), ACTION_RESULT_MIN_MILLIS);
            setResult({ content: buildResult(), isDone: true });
        } catch (error) {
            setResult(null);
            handleError(error);
            await fetch();
            setIsWorking(false);
        }
    };

    // 확인을 누른 뒤에 다시 불러와야 바뀐 상태 카드의 반응이 결과 화면에 가려지지 않는다
    const closeResult = async () => {
        setResult(null);
        await fetch();
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
                                    <Button onClick={() => void run(() => deliveryRepository.pickUp(deliveryId), () => pickUpResult(shipment))} size="medium">
                                        픽업 완료
                                    </Button>
                                </>
                            )}
                            {shipment.status === 'PICKED_UP' && (
                                <Button onClick={() => void run(() => deliveryRepository.complete(deliveryId), () => completeResult(shipment))} size="medium">
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
                            <DeliveryDistance meters={shipment.estimatedDistanceMeters} />
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
            {result && <ActionResultScreen content={result.content} isDone={result.isDone} onConfirm={() => void closeResult()} />}
        </Scaffold>
    );
}

function ShipmentStatusCard({ shipment }: { shipment: ShipmentDetail }) {
    const timeOf = { ACCEPTED: shipment.acceptedAt, PICKED_UP: shipment.pickedUpAt, DELIVERED: shipment.deliveredAt, REQUESTED: shipment.requestedAt };
    const currentIndex = shipmentSteps.indexOf(shipment.status);
    // 화면에 들어온 뒤 상태가 바뀔 때마다 늘려, 카드 링과 단계 강조를 다시 재생한다
    const [changeCount, setChangeCount] = useState(0);
    const previousStatus = useRef(shipment.status);
    useEffect(() => {
        if (previousStatus.current === shipment.status) return;
        previousStatus.current = shipment.status;
        setChangeCount((count) => count + 1);
    }, [shipment.status]);
    return (
        <section className={`${screenStyles.card} ${styles['status-card']}`} style={{ background: 'var(--primary-container)', color: 'var(--on-primary-container)' }}>
            {changeCount > 0 && <span aria-hidden className={styles['status-card__ring']} key={changeCount} />}
            <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 12 }}>
                <span className="type-label-large">{categoryText(shipment.furniture.category)}</span>
                <ShipmentStatusBadge status={shipment.status} />
            </div>
            <div className={screenStyles.card__header}>
                <FurnitureThumbnail
                    alt={shipment.furniture.itemName}
                    imageUrl={shipment.furniture.thumbnailUrl}
                    key={shipment.furniture.thumbnailUrl}
                    placeholder={categoryText(shipment.furniture.category)}
                    size={96}
                />
                <div className={`${styles.hero} ${screenStyles.card__lines}`}>
                    <p className="type-headline-small-em">{shipment.furniture.itemName}</p>
                    <p className="type-title-medium-em">{formatWon(shipment.deliveryFee)}</p>
                </div>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                <WavyProgress progress={shipmentProgress(shipment.status)} />
                <div className={styles['step-row']}>
                    {shipmentSteps.map((step, index) => (
                        <span className={styles.step} key={step} style={{ opacity: index <= currentIndex ? 1 : 0.5 }}>
                            <span
                                className={`type-label-medium ${changeCount > 0 && step === shipment.status ? styles['step--changed'] : ''}`}
                                key={step === shipment.status ? `current-${changeCount}` : step}
                                style={{ fontWeight: step === shipment.status ? 900 : 400 }}
                            >
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
