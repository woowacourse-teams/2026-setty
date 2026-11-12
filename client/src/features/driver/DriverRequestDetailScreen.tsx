import { useCallback, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { deliveryRepository } from '../../api/deliveryRepository';
import { ApiError } from '../../api/http';
import { categoryText, formatTime, shortAddress, type DeliveryRequestDetail } from '../../model/delivery';
import { formatWon } from '../../model/furniture';
import { routes, useNavigateBack, useNavigateToDriverLogin } from '../../navigation';
import {
    BackIconButton,
    BottomBar,
    Button,
    ErrorContent,
    FlexibleTopAppBar,
    FurnitureThumbnail,
    LoadingContent,
    LoadingIndicator,
    Scaffold,
    SectionTitle,
    SegmentedList,
    SegmentedListItem
} from '../../ui/components';
import { useLoad } from '../useLoad';
import screenStyles from '../screens.module.css';
import { ACTION_RESULT_MIN_MILLIS, withMinimumDuration } from './actionResult';
import { ActionResultScreen, type ActionResultContent } from './ActionResultScreen';
import { DeliveryDistance, RoutePoint, useDriverErrorHandler } from './DeliveryParts';
import styles from './driver.module.css';

function acceptResult(request: DeliveryRequestDetail): ActionResultContent {
    return {
        icon: 'inbox',
        workingTitle: '배차 수락 중이에요',
        title: '배차 수락!',
        description: '판매자에게 가구를 받아 주세요.',
        rows: [
            { label: '출발지', value: shortAddress(request.pickupAddress) },
            { label: '배송비', value: formatWon(request.deliveryFee) }
        ]
    };
}

/** 수락하기 전의 배차 요청. 연락처는 수락한 뒤 내 배차에서 볼 수 있다. */
export function DriverRequestDetailScreen() {
    const deliveryId = Number(useParams().deliveryId);
    const navigate = useNavigate();
    const navigateBack = useNavigateBack(routes.driverRequests);
    const navigateToDriverLogin = useNavigateToDriverLogin();
    const handleError = useDriverErrorHandler();
    const fetchRequest = useCallback(() => deliveryRepository.getRequest(deliveryId), [deliveryId]);
    const { state, load } = useLoad(fetchRequest, 'driver');
    const [isAccepting, setIsAccepting] = useState(false);
    const request = state.status === 'success' ? state.data : null;

    const [result, setResult] = useState<{ content: ActionResultContent; isDone: boolean } | null>(null);

    const accept = async (request: DeliveryRequestDetail) => {
        if (isAccepting) return;
        setIsAccepting(true);
        setResult({ content: acceptResult(request), isDone: false });
        try {
            await withMinimumDuration(deliveryRepository.accept(deliveryId), ACTION_RESULT_MIN_MILLIS);
            setResult({ content: acceptResult(request), isDone: true });
        } catch (error) {
            setResult(null);
            setIsAccepting(false);
            handleError(error);
            // 다른 배송원이 먼저 수락했으면 더는 이 요청을 볼 수 없으므로 목록으로 돌아간다
            if (error instanceof ApiError && error.status === 409) navigateBack();
        }
    };

    return (
        <Scaffold
            bottomBar={request && (
                <BottomBar>
                    {isAccepting ? <LoadingIndicator /> : (
                        <Button fullWidth onClick={() => void accept(request)} size="medium">배차 수락하기</Button>
                    )}
                </BottomBar>
            )}
            topBar={(
                <FlexibleTopAppBar
                    navigation={<BackIconButton onBack={navigateBack} />}
                    subtitle={request ? `주문 #${request.orderId} · ${formatTime(request.requestedAt)} 요청` : undefined}
                    title="배차 요청"
                />
            )}
        >
            {state.status === 'loading' && <LoadingContent />}
            {state.status === 'error' && (
                <ErrorContent isUnauthorized={state.isUnauthorized} message={state.message} onRequireLogin={navigateToDriverLogin} onRetry={load} />
            )}
            {request && (
                <div className={`${screenStyles['list-padding']} ${styles.stack}`}>
                    <section className={screenStyles.card} style={{ background: 'var(--primary-container)', color: 'var(--on-primary-container)' }}>
                        <div className={screenStyles.card__header}>
                            <FurnitureThumbnail
                                alt={request.furniture.itemName}
                                imageUrl={request.furniture.thumbnailUrl}
                                key={request.furniture.thumbnailUrl}
                                placeholder={categoryText(request.furniture.category)}
                                size={96}
                            />
                            <div className={screenStyles.card__lines}>
                                <p className="type-label-large">{categoryText(request.furniture.category)}</p>
                                <p className="type-headline-small-em">{request.furniture.itemName}</p>
                            </div>
                        </div>
                        <div className={styles['fee-row']}>
                            <p className="type-body-medium-em">배송비</p>
                            <p className="type-headline-small-em">{formatWon(request.deliveryFee)}</p>
                        </div>
                    </section>
                    <section className={styles.section}>
                        <SectionTitle>배송 경로</SectionTitle>
                        <DeliveryDistance meters={request.estimatedDistanceMeters} />
                        <SegmentedList>
                            <SegmentedListItem count={2} headline={<RoutePoint address={request.pickupAddress} label="출발지 · 판매자" />} index={0} />
                            <SegmentedListItem count={2} headline={<RoutePoint address={request.destinationAddress} label="도착지 · 구매자" />} index={1} />
                        </SegmentedList>
                    </section>
                </div>
            )}
            {result && (
                <ActionResultScreen
                    content={result.content}
                    isDone={result.isDone}
                    onConfirm={() => navigate(routes.driverShipmentDetail(deliveryId), { replace: true })}
                />
            )}
        </Scaffold>
    );
}
