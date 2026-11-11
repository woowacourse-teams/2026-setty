import { useState } from 'react';
import { deliveryRepository } from '../../api/deliveryRepository';
import { formatWon } from '../../model/furniture';
import { formatNotifiedAt, type DriverNotification } from '../../model/notification';
import { routes, useNavigateBack, useNavigateToDriverLogin } from '../../navigation';
import {
    BackIconButton,
    CenterFill,
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
import notificationStyles from '../notifications/notifications.module.css';

/** 기사 알림함. 지금은 배송비 정산 확정 알림뿐이라 누르면 읽음으로만 바꾸고, 수입 합계는 내 정보에서 본다. */
export function DriverNotificationsScreen() {
    const navigateBack = useNavigateBack(routes.driverMyPage);
    const navigateToDriverLogin = useNavigateToDriverLogin();
    const { state, isRefreshing, load, refresh } = useLoad(deliveryRepository.getNotifications, 'driver');
    // 서버 응답을 다시 받기 전에도 누른 알림이 바로 읽음으로 보이게 한다
    const [readIds, setReadIds] = useState<ReadonlySet<number>>(new Set());
    const isRead = (notification: DriverNotification) => notification.read || readIds.has(notification.id);

    /** 읽음 표시는 실패해도 화면에는 읽음으로 둔다. 다음에 다시 열면 서버 값으로 맞춰진다. */
    const read = (notification: DriverNotification) => {
        if (isRead(notification)) return;
        setReadIds((previous) => new Set(previous).add(notification.id));
        void deliveryRepository.readNotification(notification.id).catch(() => undefined);
    };

    return (
        <Scaffold topBar={<FlexibleTopAppBar navigation={<BackIconButton onBack={navigateBack} />} title="받은 알림" />}>
            {state.status === 'loading' && <LoadingContent />}
            {state.status === 'error' && (
                <ErrorContent isUnauthorized={state.isUnauthorized} message={state.message} onRequireLogin={navigateToDriverLogin} onRetry={load} />
            )}
            {state.status === 'success' && (
                <PullToRefresh isRefreshing={isRefreshing} onRefresh={refresh}>
                    <div className={screenStyles['list-padding']}>
                        {state.data.length === 0 && (
                            <CenterFill>
                                <SettyMessage
                                    description="구매자가 구매를 확정해 배송비 정산이 확정되면 여기에 쌓여요."
                                    title="아직 받은 알림이 없어요"
                                />
                            </CenterFill>
                        )}
                        <SegmentedList>
                            {state.data.map((notification, index) => (
                                <SegmentedListItem
                                    count={state.data.length}
                                    headline={<span className="type-title-medium">{notification.headline}</span>}
                                    index={index}
                                    key={notification.id}
                                    leading={!isRead(notification) && (
                                        <span aria-label="안 읽음" className={notificationStyles['unread-dot']} role="img" />
                                    )}
                                    onClick={() => read(notification)}
                                    overline={`배송비 정산 · ${formatNotifiedAt(notification.createdAt)}`}
                                    style={isRead(notification) ? undefined : { background: 'color-mix(in srgb, var(--primary) 10%, var(--surface-container))' }}
                                    supporting={notification.amount === null
                                        ? undefined
                                        : <span className="type-label-medium">{formatWon(notification.amount)}</span>}
                                />
                            ))}
                        </SegmentedList>
                    </div>
                </PullToRefresh>
            )}
        </Scaffold>
    );
}
