package setty.platform.order.controller.dto;

import setty.platform.listing.domain.Listing;
import setty.platform.order.domain.Order;

public record MyOrderResponse(
        Long id,
        ListingInfo listing,
        String deliveryStatus
) {

    public record ListingInfo(
            Long id,
            String name,
            Integer price,
            Integer deliveryFee
    ) {
    }

    public static MyOrderResponse of(final Order order, final Listing listing) {
        return new MyOrderResponse(
                order.getId(),
                listing == null ? null : new ListingInfo(
                        listing.getId(),
                        listing.getTitle(),
                        listing.getPrice(),
                        listing.getDeliveryFee()
                ),
                order.getDeliveryStatus().name()
        );
    }
}
