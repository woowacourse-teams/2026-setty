package setty.common;

import java.time.Instant;

public record OrderStatusNotified(
        Long orderId,
        Long listingId,
        Long buyerId,
        Long sellerId,
        String listingTitle,
        Change change,
        Instant occurredAt
) {

    public enum Change {
        CONFIRMED,
        DELIVERY_ACCEPTED,
        DELIVERY_PICKED_UP,
        DELIVERY_DELIVERED,
        CANCELLED_BY_BUYER,
        CANCELLATION_REJECTED,
        CANCELLED_BY_SELLER
    }
}
