package setty.platform.order.service;

import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.platform.listing.application.ListingService;
import setty.platform.order.domain.Order;
import setty.platform.order.repository.OrderRepository;

@Service
public class PendingOrderExpirationService {

    private final OrderRepository orderRepository;
    private final ListingService listingService;

    public PendingOrderExpirationService(
            final OrderRepository orderRepository,
            final ListingService listingService
    ) {
        this.orderRepository = orderRepository;
        this.listingService = listingService;
    }

    @Transactional
    public boolean expire(final Long orderId, final Instant referenceTime) {
        final Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || !order.expire(referenceTime)) {
            return false;
        }

        listingService.releasePurchaseRequestForExpiredPendingOrder(order.getListingId());
        return true;
    }

    @Transactional
    public int expireBatch(final List<Long> orderIds, final Instant referenceTime) {
        if (orderIds.isEmpty()) {
            return 0;
        }
        // 후보 조회 이후 결제가 확정됐을 수 있으므로, 잠금을 얻은 최신 상태로 다시 판단한다.
        final List<Order> expiredOrders = orderRepository.findAllByIdForUpdate(orderIds).stream()
                .filter(order -> order.canExpire(referenceTime))
                .toList();
        if (expiredOrders.isEmpty()) {
            return 0;
        }
        final List<Long> expiredIds = expiredOrders.stream().map(Order::getId).toList();
        final List<Long> listingIds = expiredOrders.stream().map(Order::getListingId).toList();
        final int updated = orderRepository.expirePendingOrders(expiredIds, referenceTime);
        if (updated != expiredIds.size()) {
            throw new IllegalStateException("잠긴 만료 주문 수와 변경된 주문 수가 일치하지 않습니다");
        }
        listingService.releasePurchaseRequestsForExpiredPendingOrders(listingIds);
        return updated;
    }
}
