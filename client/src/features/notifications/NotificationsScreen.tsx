import { useNavigate } from 'react-router';
import { notificationRepository } from '../../api/repositories';
import { formatWon } from '../../model/furniture';
import { formatNotifiedAt, type Notification } from '../../model/notification';
import { routes, useNavigateBack, useNavigateToLogin } from '../../navigation';
import {
    BackIconButton,
    ErrorContent,
    FlexibleTopAppBar,
    LoadingContent,
    PullToRefresh,
    Scaffold,
    SegmentedList,
    SegmentedListItem,
    SettyMessage
} from '../../ui/components';
import { useLoad } from '../useLoad';
import screenStyles from '../screens.module.css';

export function NotificationsScreen() {
    const navigate = useNavigate();
    const navigateBack = useNavigateBack();
    const navigateToLogin = useNavigateToLogin();
    const { state, isRefreshing, load, refresh } = useLoad(notificationRepository.getMine);

    /** 열어 봤다는 표시는 실패해도 화면 이동을 막지 않는다. */
    const open = (notification: Notification) => {
        void notificationRepository.ack(notification.id).catch(() => undefined);
        navigate(routes.furnitureDetail(notification.listingId));
    };

    return (
        <Scaffold topBar={<FlexibleTopAppBar navigation={<BackIconButton onBack={navigateBack} />} title="알림함" />}>
            {state.status === 'loading' && <LoadingContent />}
            {state.status === 'error' && (
                <ErrorContent isUnauthorized={state.isUnauthorized} message={state.message} onRequireLogin={navigateToLogin} onRetry={load} />
            )}
            {state.status === 'success' && (
                <PullToRefresh isRefreshing={isRefreshing} onRefresh={refresh}>
                    <div className={screenStyles['list-padding']}>
                        {state.data.length === 0 && (
                            <SettyMessage
                                description="키워드를 등록해 두면 새 매물이 올라올 때 여기에 쌓여요."
                                style={{ marginTop: 48 }}
                                title="아직 받은 알림이 없어요"
                            />
                        )}
                        <SegmentedList>
                            {state.data.map((notification, index) => (
                                <SegmentedListItem
                                    count={state.data.length}
                                    headline={<span className="type-title-medium">{notification.title ?? '삭제된 가구'}</span>}
                                    index={index}
                                    key={notification.id}
                                    onClick={() => open(notification)}
                                    overline={`관심 키워드의 새 매물 · ${formatNotifiedAt(notification.createdAt)}`}
                                    supporting={notification.price !== null && (
                                        <>
                                            <span className={`${screenStyles['price-primary']} type-body-large-em`}>{formatWon(notification.price)}</span>
                                            {notification.deliveryFee !== null && (
                                                <span className="type-label-medium"> · 배송비 {formatWon(notification.deliveryFee)}</span>
                                            )}
                                        </>
                                    )}
                                />
                            ))}
                        </SegmentedList>
                    </div>
                </PullToRefresh>
            )}
        </Scaffold>
    );
}
