package setty.platform.order.controller.dto;

import setty.platform.order.domain.Order;

public record OrderCreateResponse(
        Long id,
        Long listingId,
        Long buyerId,
        String deliveryStatus,
        Integer deliveryFee,
        int totalPrice
) {

    public static OrderCreateResponse from(final Order order, final int totalPrice) {
        return new OrderCreateResponse(
                order.getId(),
                order.getListingId(),
                order.getBuyerId(),
                order.getDeliveryStatus().name(),
                order.getDeliveryFee(),
                totalPrice
        );
    }
}
