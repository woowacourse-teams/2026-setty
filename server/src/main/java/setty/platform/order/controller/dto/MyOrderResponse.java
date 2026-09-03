package setty.platform.order.controller.dto;

import setty.platform.listing.application.ListingView;
import setty.platform.order.domain.Order;

public record MyOrderResponse(
        Long id,
        ListingInfo listing,
        String deliveryStatus
) {

    public record ListingInfo(
            Long id,
            String name,
            String thumbnailUrl,
            Integer price,
            Integer deliveryFee
    ) {
    }

    public static MyOrderResponse of(final Order order, final ListingView.Summary listing) {
        return new MyOrderResponse(
                order.getId(),
                listing == null ? null : new ListingInfo(
                        listing.id(),
                        listing.title(),
                        listing.thumbnailUrl(),
                        listing.price(),
                        listing.deliveryFee()
                ),
                order.getDeliveryStatus().name()
        );
    }
}
