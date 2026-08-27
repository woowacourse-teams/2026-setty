package setty.platform.listing.application;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import setty.common.DeliveryStatusChanged;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.platform.order.domain.Order;
import setty.platform.order.repository.OrderRepository;

@Component
@RequiredArgsConstructor
public class ListingDeliveryStatusChangedListener {

    private final OrderRepository orderRepository;
    private final ListingService listingService;

    @EventListener
    @Transactional
    public void handle(final DeliveryStatusChanged event) {
        if (event == null || event.status() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        switch (event.status()) {
            case "ACCEPTED" -> listingService.reserveForDelivery(findListingId(event.orderId()));
            case "DELIVERED" -> listingService.completeSale(findListingId(event.orderId()));
            case "PICKED_UP" -> {
                // PICKED_UP changes only the delivery and order statuses.
            }
            default -> throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private Long findListingId(final Long orderId) {
        if (orderId == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return orderRepository.findById(orderId)
                .map(Order::getListingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }
}
