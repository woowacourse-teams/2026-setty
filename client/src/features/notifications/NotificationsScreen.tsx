import { useState } from 'react';
import { useNavigate } from 'react-router';
import { notificationRepository } from '../../api/repositories';
import { formatWon } from '../../model/furniture';
import { formatNotifiedAt, isNotifiedListingClosed, type Notification } from '../../model/notification';
import { routes, useNavigateBack, useNavigateToLogin } from '../../navigation';
import { readAllNotifications, readNotification } from '../../push/unreadNotifications';
import {
    BackIconButton,
    Button,
    CenterFill,
    ErrorContent,
    FlexibleTopAppBar,
    LoadingContent,
    PullToRefresh,
    SaleStatusBadge,
    Scaffold,
    SegmentedList,
    SegmentedListItem,
    SettyMessage
} from '../../ui/components';
import { useLoad } from '../useLoad';
import screenStyles from '../screens.module.css';
import styles from './notifications.module.css';

export function NotificationsScreen() {
    const navigate = useNavigate();
    const navigateBack = useNavigateBack();
    const navigateToLogin = useNavigateToLogin();
    const { state, isRefreshing, load, refresh } = useLoad(notificationRepository.getMine);

    // 서버 응답을 다시 받기 전에도 누른 알림이 바로 읽음으로 보이게 한다
    const [readIds, setReadIds] = useState<ReadonlySet<number>>(new Set());
    const isRead = (notification: Notification) => notification.read || readIds.has(notification.id);
    const hasUnread = state.status === 'success' && state.data.some((notification) => !isRead(notification));

    /** 읽음 표시는 실패해도 화면 이동을 막지 않는다. */
    const open = (notification: Notification) => {
        void notificationRepository.ack(notification.id).catch(() => undefined);
        if (!isRead(notification)) void readNotification(notification.id);
        navigate(routes.furnitureDetail(notification.listingId));
    };

    const readAll = () => {
        if (state.status !== 'success') return;
        setReadIds(new Set(state.data.map((notification) => notification.id)));
        void readAllNotifications();
    };

    return (
        <Scaffold
            topBar={(
                <FlexibleTopAppBar
                    actions={hasUnread && <Button onClick={readAll} variant="text">모두 읽음</Button>}
                    navigation={<BackIconButton onBack={navigateBack} />}
                    title="알림함"
                />
            )}
        >
            {state.status === 'loading' && <LoadingContent />}
            {state.status === 'error' && (
                <ErrorContent isUnauthorized={state.isUnauthorized} message={state.message} onRequireLogin={navigateToLogin} onRetry={load} />
            )}
            {state.status === 'success' && (
                <PullToRefresh isRefreshing={isRefreshing} onRefresh={refresh}>
                    <div className={screenStyles['list-padding']}>
                        {state.data.length === 0 && (
                            <CenterFill>
                                <SettyMessage
                                    description="키워드를 등록해 두면 새 매물이 올라올 때 여기에 쌓여요."
                                    title="아직 받은 알림이 없어요"
                                />
                            </CenterFill>
                        )}
                        <SegmentedList>
                            {state.data.map((notification, index) => (
                                <SegmentedListItem
                                    count={state.data.length}
                                    headline={(
                                        <span className={closedClass(notification)}>
                                            <span className="type-title-medium">{notification.title ?? '삭제된 가구'}</span>
                                        </span>
                                    )}
                                    index={index}
                                    key={notification.id}
                                    leading={!isRead(notification) && <span aria-label="안 읽음" className={styles['unread-dot']} role="img" />}
                                    onClick={() => open(notification)}
                                    overline={<span className={closedClass(notification)}>{`관심 키워드의 새 매물 · ${formatNotifiedAt(notification.createdAt)}`}</span>}
                                    style={isRead(notification) ? undefined : { background: 'color-mix(in srgb, var(--primary) 10%, var(--surface-container))' }}
                                    supporting={<NotifiedListingSummary notification={notification} />}
                                />
                            ))}
                        </SegmentedList>
                    </div>
                </PullToRefresh>
            )}
        </Scaffold>
    );
}

function closedClass(notification: Notification): string | undefined {
    return isNotifiedListingClosed(notification.listingStatus) ? styles.closed : undefined;
}

/** 판매중이면 가격과 배송비, 거래중·판매완료면 배지와 흐린 가격, 삭제됐으면 안내 문구를 보여 준다. */
function NotifiedListingSummary({ notification }: { notification: Notification }) {
    if (notification.listingStatus === 'DELETED' || notification.price === null) {
        return <span className={`${styles.closed} type-label-medium`}>삭제된 매물이에요</span>;
    }
    const isClosed = isNotifiedListingClosed(notification.listingStatus);
    return (
        <span className={styles.summary}>
            {notification.listingStatus === 'TRADING' && <SaleStatusBadge saleStatus="RESERVED" />}
            {notification.listingStatus === 'SOLD' && <SaleStatusBadge saleStatus="SOLD" />}
            <span className={isClosed ? styles.closed : undefined}>
                <span className={`${isClosed ? '' : screenStyles['price-primary']} type-body-large-em`}>{formatWon(notification.price)}</span>
                {notification.deliveryFee !== null && (
                    <span className="type-label-medium"> · 배송비 {formatWon(notification.deliveryFee)}</span>
                )}
            </span>
        </span>
    );
}
