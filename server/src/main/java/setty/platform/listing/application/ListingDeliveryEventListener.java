package setty.platform.listing.application;

import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import setty.common.DeliveryAccepted;
import setty.common.DeliveryDelivered;
import setty.common.OrderCancelled;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.platform.order.domain.Order;
import setty.platform.order.repository.OrderRepository;

@Component
@RequiredArgsConstructor
public class ListingDeliveryEventListener {

    private final OrderRepository orderRepository;
    private final ListingService listingService;

    @ApplicationModuleListener
    public void onDeliveryAccepted(final DeliveryAccepted event) {
        validateDeliveryEvent(event == null ? null : event.deliveryId(), event == null ? null : event.orderId());
        listingService.reserveForDelivery(findListingId(event.orderId()));
    }

    @ApplicationModuleListener
    public void onDeliveryDelivered(final DeliveryDelivered event) {
        validateDeliveryEvent(event == null ? null : event.deliveryId(), event == null ? null : event.orderId());
        listingService.completeSale(findListingId(event.orderId()));
    }

    @ApplicationModuleListener
    public void onOrderCancelled(final OrderCancelled event) {
        if (event == null || event.listingId() == null || event.listingId() <= 0
                || event.orderId() == null || event.orderId() <= 0
                || event.cancellationRequestId() == null || event.cancellationRequestId().isBlank()
                || event.cancelledAt() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        listingService.releasePurchaseRequest(event.listingId());
    }

    private void validateDeliveryEvent(final Long deliveryId, final Long orderId) {
        if (deliveryId == null || deliveryId <= 0 || orderId == null || orderId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private Long findListingId(final Long orderId) {
        return orderRepository.findById(orderId)
                .map(Order::getListingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }
}
