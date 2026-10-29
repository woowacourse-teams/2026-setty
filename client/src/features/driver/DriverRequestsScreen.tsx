import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import { deliveryRepository, subscribeRequestEvents } from '../../api/deliveryRepository';
import { routes, useNavigateToDriverLogin } from '../../navigation';
import {
    ErrorContent,
    LoadingContent,
    PullToRefresh,
    Scaffold,
    SegmentedList,
    SettyMessage,
    TopAppBar
} from '../../ui/components';
import { useLoad } from '../useLoad';
import { usePolling } from '../usePolling';
import screenStyles from '../screens.module.css';
import { DeliveryListItem } from './DeliveryParts';
import { DriverTabBar } from './DriverTabBar';

/** 배송원이 수락할 수 있는 배차 요청. 구매자가 결제하면 새로고침하지 않아도 바로 나타난다. */
export function DriverRequestsScreen() {
    const navigate = useNavigate();
    const navigateToDriverLogin = useNavigateToDriverLogin();
    const { state, isRefreshing, load, refresh, fetch } = useLoad(deliveryRepository.getRequests, 'driver');

    const [isLive, setIsLive] = useState(false);

    useEffect(() => subscribeRequestEvents(() => void fetch(), navigateToDriverLogin, setIsLive), [fetch, navigateToDriverLogin]);
    // 실시간 연결이 끊겼거나 서버가 연결 수 제한으로 거절하면 대신 주기적으로 불러온다
    usePolling(() => void fetch(), state.status === 'success' && !isLive);

    return (
        <Scaffold
            bottomBar={<DriverTabBar current="requests" />}
            topBar={<TopAppBar subtitle="새 요청이 오면 바로 보여드려요" title={<span className="type-title-large-em">배차 요청</span>} />}
        >
            {state.status === 'loading' && <LoadingContent />}
            {state.status === 'error' && (
                <ErrorContent isUnauthorized={state.isUnauthorized} message={state.message} onRequireLogin={navigateToDriverLogin} onRetry={load} />
            )}
            {state.status === 'success' && (
                <PullToRefresh isRefreshing={isRefreshing} onRefresh={refresh}>
                    <div className={screenStyles['list-padding']}>
                        {state.data.length === 0 && (
                            <SettyMessage
                                description={'구매자가 가구를 결제하면\n이곳에 배차 요청이 나타나요.'}
                                style={{ marginTop: 48 }}
                                title="지금은 배차 요청이 없어요"
                            />
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
                    </div>
                </PullToRefresh>
            )}
        </Scaffold>
    );
}
