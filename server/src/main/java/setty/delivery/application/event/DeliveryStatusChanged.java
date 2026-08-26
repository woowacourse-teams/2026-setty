package setty.delivery.application.event;

import java.time.Instant;

public record DeliveryStatusChanged(
        Long deliveryId,
        Long orderId,
        String status,
        Instant changedAt
) {
}
