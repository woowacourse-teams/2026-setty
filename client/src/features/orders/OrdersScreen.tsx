import { useNavigate } from 'react-router';
import { orderRepository } from '../../api/repositories';
import { formatWon } from '../../model/furniture';
import { isCancelFlow, isDeliveryInProgress, orderedTotalPrice, orderStatusLabel, type Order } from '../../model/order';
import { routes, useNavigateBack, useNavigateToLogin } from '../../navigation';
import {
    BackIconButton,
    ErrorContent,
    FlexibleTopAppBar,
    FurnitureThumbnail,
    LoadingContent,
    PullToRefresh,
    Scaffold,
    SegmentedList,
    SegmentedListItem,
    SettyMessage,
    StatusBadge
} from '../../ui/components';
import { useLoad } from '../useLoad';
import { usePolling } from '../usePolling';
import screenStyles from '../screens.module.css';

export function OrdersScreen() {
    const navigate = useNavigate();
    const navigateBack = useNavigateBack();
    const navigateToLogin = useNavigateToLogin();
    const { state, isRefreshing, load, refresh, fetch } = useLoad(orderRepository.getMyOrders);
    usePolling(() => void fetch(), state.status === 'success' && state.data.some(isDeliveryInProgress));

    return (
        <Scaffold topBar={<FlexibleTopAppBar navigation={<BackIconButton onBack={navigateBack} />} title="주문 내역" />}>
            {state.status === 'loading' && <LoadingContent />}
            {state.status === 'error' && (
                <ErrorContent isUnauthorized={state.isUnauthorized} message={state.message} onRequireLogin={navigateToLogin} onRetry={load} />
            )}
            {state.status === 'success' && (
                <PullToRefresh isRefreshing={isRefreshing} onRefresh={refresh}>
                    <div className={screenStyles['list-padding']}>
                        {state.data.length === 0 && (
                            <SettyMessage description="마음에 드는 가구를 찾아 주문해 보세요." style={{ marginTop: 48 }} title="아직 주문한 가구가 없어요" />
                        )}
                        <SegmentedList>
                            {state.data.map((order, index) => (
                                <SegmentedListItem
                                    count={state.data.length}
                                    headline={<span className="type-title-medium">{order.listing?.name ?? '삭제된 가구'}</span>}
                                    index={index}
                                    key={order.id}
                                    leading={<FurnitureThumbnail alt={order.listing?.name} imageUrl={order.listing?.thumbnailUrl} placeholder="가구" />}
                                    onClick={() => navigate(routes.orderDetail(order.id))}
                                    overline={`주문 #${order.id}`}
                                    supporting={order.listing && (
                                        <span className={`${screenStyles['price-primary']} type-body-large-em`}>
                                            {formatWon(orderedTotalPrice(order.listing))}
                                        </span>
                                    )}
                                    trailing={<OrderStatusBadge order={order} />}
                                />
                            ))}
                        </SegmentedList>
                    </div>
                </PullToRefresh>
            )}
        </Scaffold>
    );
}

export function OrderStatusBadge({ order }: { order: Order }) {
    let colors: [string, string];
    if (isCancelFlow(order)) {
        colors = ['var(--error-container)', 'var(--on-error-container)'];
    } else if (order.deliveryStatus === 'DELIVERED') {
        colors = ['var(--surface-container-highest)', 'var(--on-surface-variant)'];
    } else {
        colors = ['var(--primary-container)', 'var(--on-primary-container)'];
    }
    return <StatusBadge containerColor={colors[0]} contentColor={colors[1]} text={orderStatusLabel(order)} />;
}
