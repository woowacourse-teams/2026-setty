package setty.common;

import java.time.Instant;

public record DeliveryDelivered(
        Long deliveryId,
        Long orderId,
        Instant changedAt,
        Long driverId,
        int deliveryFee
) {
}
