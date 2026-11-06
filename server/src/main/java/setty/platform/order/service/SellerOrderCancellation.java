package setty.platform.order.service;

import java.time.Instant;
import org.springframework.stereotype.Component;
import setty.common.OrderStatusNotified;
import setty.platform.order.domain.Order;
import setty.platform.order.repository.OrderRepository;

@Component
public class SellerOrderCancellation {

    private final OrderRepository orderRepository;
    private final OrderStatusNotifier orderStatusNotifier;

    public SellerOrderCancellation(final OrderRepository orderRepository, final OrderStatusNotifier orderStatusNotifier) {
        this.orderRepository = orderRepository;
        this.orderStatusNotifier = orderStatusNotifier;
    }

    // 매물을 잠근 트랜잭션 안에서 부른다. 주문 생성과 같은 순서(매물 → 주문)로 잠근다.
    // 만료 전 결제 대기 주문은 취소하고 구매자에게 알리며, 만료 시각이 지난 주문은 만료 처리한다.
    public void cancelPendingOrdersOf(final Long listingId, final Instant referenceTime) {
        for (final Order order : orderRepository.findPendingByListingIdForUpdate(listingId)) {
            if (order.cancelBySeller(referenceTime)) {
                orderStatusNotifier.notify(order, OrderStatusNotified.Change.CANCELLED_BY_SELLER, referenceTime);
                continue;
            }
            order.expire(referenceTime);
        }
    }
}
