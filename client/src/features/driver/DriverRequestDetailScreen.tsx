import { useCallback, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { deliveryRepository } from '../../api/deliveryRepository';
import { ApiError } from '../../api/http';
import { categoryText, formatTime } from '../../model/delivery';
import { formatWon } from '../../model/furniture';
import { routes, useNavigateBack, useNavigateToDriverLogin } from '../../navigation';
import {
    BackIconButton,
    BottomBar,
    Button,
    ErrorContent,
    FlexibleTopAppBar,
    LoadingContent,
    LoadingIndicator,
    Scaffold,
    SectionTitle,
    SegmentedList,
    SegmentedListItem
} from '../../ui/components';
import { showSnackbar } from '../../ui/snackbar';
import { useLoad } from '../useLoad';
import screenStyles from '../screens.module.css';
import { RoutePoint, useDriverErrorHandler } from './DeliveryParts';
import styles from './driver.module.css';

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

    const accept = async () => {
        if (isAccepting) return;
        setIsAccepting(true);
        try {
            await deliveryRepository.accept(deliveryId);
            navigate(routes.driverShipmentDetail(deliveryId), { replace: true });
            showSnackbar('배차를 수락했어요. 판매자에게 가구를 받아주세요.', { keepOnNextScreen: true });
        } catch (error) {
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
                        <Button fullWidth onClick={() => void accept()} size="medium">배차 수락하기</Button>
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
                        <div className={styles.hero}>
                            <p className="type-label-large">{categoryText(request.furniture.category)}</p>
                            <p className="type-headline-small-em">{request.furniture.itemName}</p>
                        </div>
                        <div className={styles.summary}>
                            <div className={styles.summary__item}>
                                <p className="type-label-medium">배송비</p>
                                <p className="type-title-large-em">{formatWon(request.deliveryFee)}</p>
                            </div>
                        </div>
                    </section>
                    <section className={styles.section}>
                        <SectionTitle>배송 경로</SectionTitle>
                        <SegmentedList>
                            <SegmentedListItem count={2} headline={<RoutePoint address={request.pickupAddress} label="출발지 · 판매자" />} index={0} />
                            <SegmentedListItem count={2} headline={<RoutePoint address={request.destinationAddress} label="도착지 · 구매자" />} index={1} />
                        </SegmentedList>
                    </section>
                </div>
            )}
        </Scaffold>
    );
}
