package setty.platform.order.controller.dto;

import setty.platform.order.domain.Order;

public record MyOrderResponse(
        Long id,
        Long listingId,
        String deliveryStatus
) {

    public static MyOrderResponse from(final Order order) {
        return new MyOrderResponse(
                order.getId(),
                order.getListingId(),
                order.getDeliveryStatus().name()
        );
    }
}
