package setty.common;

import java.time.Instant;

public record DeliveryAccepted(
        Long deliveryId,
        Long orderId,
        Instant changedAt
) {
}
