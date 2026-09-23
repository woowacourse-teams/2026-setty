package setty.platform.order.service;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.platform.listing.application.ListingService;
import setty.platform.order.domain.Order;
import setty.platform.order.repository.OrderRepository;

@Service
public class PendingOrderExpirationService {

    private static final Logger log = LoggerFactory.getLogger(PendingOrderExpirationService.class);

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
        if (order == null || !order.canExpire(referenceTime)) {
            return false;
        }
        if (orderRepository.countPaymentReferences(orderId) > 0) {
            log.warn("결제 행이 존재하는 PENDING 주문은 자동 만료하지 않습니다. orderId={}", orderId);
            return false;
        }

        listingService.releasePurchaseRequestForExpiredPendingOrder(order.getListingId());
        orderRepository.delete(order);
        return true;
    }
}
