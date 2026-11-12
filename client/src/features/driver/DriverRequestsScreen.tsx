import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import { deliveryRepository, subscribeRequestEvents } from '../../api/deliveryRepository';
import { deliveryLoginIdStore } from '../../api/http';
import { routes, useNavigateToDriverLogin } from '../../navigation';
import {
    CenterFill,
    ErrorContent,
    ExtendedFab,
    LoadingContent,
    PullToRefresh,
    Scaffold,
    SectionTitle,
    SegmentedList,
    SettyMessage,
    TopAppBar
} from '../../ui/components';
import type { DeliverySummary } from '../../model/delivery';
import { Icon } from '../../ui/Icon';
import { shapeStyle } from '../../ui/shapes';
import { usePolling } from '../usePolling';
import screenStyles from '../screens.module.css';
import { DeliveryListItem } from './DeliveryParts';
import { DeliveryLoadMore } from './DeliveryLoadMore';
import { requestPageStore } from './deliveryPageStore';
import { useDeliveryPage } from './useDeliveryPage';
import { useDriverLocation } from './useDriverLocation';
import { DriverNotificationsButton } from './DriverNotificationsButton';
import styles from './driver.module.css';

/**
 * 배송원이 수락할 수 있는 배차 요청. 구매자가 결제하면 새로고침하지 않아도 바로 나타난다.
 * 진행 중 배차가 있으면 지금 칸에서 출발해 내 도착지 근처로 가는 요청을 "가는 길에 실을 콜"로 위에 따로 보여 준다.
 */
export function DriverRequestsScreen() {
    const navigate = useNavigate();
    const navigateToDriverLogin = useNavigateToDriverLogin();
    const { state, nextCursor, isRefreshing, isLoadingMore, hasLoadMoreError, load, refresh, loadMore } = useDeliveryPage(requestPageStore);

    const profileInitial = deliveryLoginIdStore.get().slice(0, 1).toUpperCase();

    const [isLive, setIsLive] = useState(false);
    const [isFabExpanded, setIsFabExpanded] = useState(true);
    const [bundle, setBundle] = useState<DeliverySummary[]>([]);

    // 묶음 후보는 보조 정보라 실패해도 목록 화면을 막지 않는다
    const loadBundle = useCallback(() => {
        deliveryRepository.getBundleCandidates().then((result) => setBundle(result.items)).catch(() => {});
    }, []);
    const refreshAll = useCallback(() => {
        loadBundle();
        return refresh();
    }, [refresh, loadBundle]);

    useDriverLocation();
    // 목록이 바뀌면(내가 수락·완료해 도착지가 바뀐 경우 포함) 후보도 다시 불러온다
    useEffect(
        () => subscribeRequestEvents(refreshAll, navigateToDriverLogin, setIsLive, loadBundle),
        [refreshAll, navigateToDriverLogin, loadBundle]
    );
    // 실시간 연결이 끊겼거나 서버가 연결 수 제한으로 거절하면 대신 주기적으로 불러온다
    usePolling(refreshAll, state.status === 'success' && !isLive);

    useEffect(() => {
        const onScroll = () => setIsFabExpanded(window.scrollY < 120);
        window.addEventListener('scroll', onScroll, { passive: true });
        return () => window.removeEventListener('scroll', onScroll);
    }, []);

    return (
        <Scaffold
            floatingActionButton={<ExtendedFab expanded={isFabExpanded} icon="localShipping" label="내 배차" onClick={() => navigate(routes.driverShipments)} />}
            topBar={(
                <TopAppBar
                    actions={(
                        <>
                            <DriverNotificationsButton onClick={() => navigate(routes.driverNotifications)} />
                            <button aria-label="내 정보" className={styles['profile-button']} onClick={() => navigate(routes.driverMyPage)} type="button">
                                <span className={styles['profile-button__shape']} style={shapeStyle('cookie9')}>
                                    {profileInitial || <Icon name="accountCircle" size={20} />}
                                </span>
                            </button>
                        </>
                    )}
                    height={72}
                    subtitle="가까운 가구 배송을 골라 가볍게"
                    title={<span className={styles.brand}>SETTY</span>}
                />
            )}
        >
            {state.status === 'loading' && <LoadingContent />}
            {state.status === 'error' && (
                <ErrorContent isUnauthorized={state.isUnauthorized} message={state.message} onRequireLogin={navigateToDriverLogin} onRetry={load} />
            )}
            {state.status === 'success' && (
                <PullToRefresh isRefreshing={isRefreshing} onRefresh={refreshAll}>
                    <div className={screenStyles['list-padding']}>
                        {bundle.length > 0 && (
                            <section aria-label="가는 길에 실을 콜">
                                <SectionTitle>가는 길에 실을 콜</SectionTitle>
                                <SegmentedList>
                                    {bundle.map((delivery, index) => (
                                        <DeliveryListItem
                                            count={bundle.length}
                                            delivery={delivery}
                                            index={index}
                                            key={delivery.deliveryId}
                                            onClick={() => navigate(routes.driverRequestDetail(delivery.deliveryId))}
                                        />
                                    ))}
                                </SegmentedList>
                                <SectionTitle>모든 배차 요청</SectionTitle>
                            </section>
                        )}
                        {state.data.length === 0 && (
                            <CenterFill>
                                <SettyMessage
                                    description={'구매자가 가구를 결제하면\n이곳에 배차 요청이 나타나요.'}
                                    title="지금은 배차 요청이 없어요"
                                />
                            </CenterFill>
                        )}
                        <SegmentedList>
                            {state.data.map((delivery, index) => (
                                <DeliveryListItem
                                    count={state.data.length}
                                    delivery={delivery}
                                    index={index}
                                    key={delivery.deliveryId}
                                    onClick={() => navigate(routes.driverRequestDetail(delivery.deliveryId))}
                                />
                            ))}
                        </SegmentedList>
                        <DeliveryLoadMore
                            hasError={hasLoadMoreError}
                            hasMore={nextCursor !== null}
                            isLoading={isLoadingMore || isRefreshing}
                            onLoad={loadMore}
                        />
                    </div>
                </PullToRefresh>
            )}
        </Scaffold>
    );
}
