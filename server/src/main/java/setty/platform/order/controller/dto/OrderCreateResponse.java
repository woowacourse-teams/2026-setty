package setty.platform.order.controller.dto;

import setty.platform.order.domain.Order;

public record OrderCreateResponse(
        Long id,
        Long listingId,
        Long buyerId,
        String deliveryStatus
) {

    public static OrderCreateResponse from(final Order order) {
        return new OrderCreateResponse(
                order.getId(),
                order.getListingId(),
                order.getBuyerId(),
                order.getDeliveryStatus().name()
        );
    }
}
