package setty.platform.order.service;

import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.platform.listing.application.ListingService;
import setty.platform.listing.repository.ListingRepository;
import setty.platform.order.domain.Order;
import setty.platform.order.repository.OrderRepository;

/**
 * 주문 생성·판매자 삭제와 같은 순서(매물 → 주문)로 잠가 서로 기다리다 멈추지 않게 한다.
 * 매물 id는 잠그지 않고 읽는다. 주문 엔티티를 먼저 읽어 두면 잠근 뒤에도 그 상태가 남아,
 * 그사이 결제가 확정된 주문을 만료시킬 수 있다.
 */
@Service
public class PendingOrderExpirationService {

    private final OrderRepository orderRepository;
    private final ListingRepository listingRepository;
    private final ListingService listingService;

    public PendingOrderExpirationService(
            final OrderRepository orderRepository,
            final ListingRepository listingRepository,
            final ListingService listingService
    ) {
        this.orderRepository = orderRepository;
        this.listingRepository = listingRepository;
        this.listingService = listingService;
    }

    @Transactional
    public boolean expire(final Long orderId, final Instant referenceTime) {
        final Long listingId = orderRepository.findListingIdById(orderId).orElse(null);
        if (listingId == null) {
            return false;
        }
        listingRepository.findByIdForUpdate(listingId);
        final Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || !order.expire(referenceTime)) {
            return false;
        }

        listingService.releasePurchaseRequestForExpiredPendingOrder(listingId);
        return true;
    }

    @Transactional
    public int expireBatch(final List<Long> orderIds, final Instant referenceTime) {
        if (orderIds.isEmpty()) {
            return 0;
        }
        final List<Long> listingIds = orderRepository.findListingIdsByIdIn(orderIds);
        if (listingIds.isEmpty()) {
            return 0;
        }
        listingRepository.findAllByIdForUpdate(listingIds);
        // 후보 조회 이후 결제가 확정됐을 수 있으므로, 잠금을 얻은 최신 상태로 다시 판단한다.
        final List<Order> expiredOrders = orderRepository.findAllByIdForUpdate(orderIds).stream()
                .filter(order -> order.canExpire(referenceTime))
                .toList();
        if (expiredOrders.isEmpty()) {
            return 0;
        }
        final List<Long> expiredIds = expiredOrders.stream().map(Order::getId).toList();
        final List<Long> expiredListingIds = expiredOrders.stream().map(Order::getListingId).toList();
        final int updated = orderRepository.expirePendingOrders(expiredIds, referenceTime);
        if (updated != expiredIds.size()) {
            throw new IllegalStateException("잠긴 만료 주문 수와 변경된 주문 수가 일치하지 않습니다");
        }
        listingService.releasePurchaseRequestsForExpiredPendingOrders(expiredListingIds);
        return updated;
    }
}
