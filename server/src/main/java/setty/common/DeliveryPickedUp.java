package setty.common;

import java.time.Instant;

public record DeliveryPickedUp(
        Long deliveryId,
        Long orderId,
        Instant changedAt
) {
}
