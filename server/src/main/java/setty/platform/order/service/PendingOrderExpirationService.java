package setty.platform.order.service;

import java.time.Instant;
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
}
