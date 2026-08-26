package setty.delivery.query;

import java.time.Instant;
import setty.common.DeliveryStatus;

public final class DeliveryRequest {

    private DeliveryRequest() {
    }

    public record Summary(
            long deliveryId,
            String itemName,
            String category,
            String pickupAddress,
            String deliveryAddress,
            long deliveryFee,
            DeliveryStatus status,
            Instant requestedAt
    ) {
    }

    public record Detail(
            long deliveryId,
            long orderId,
            String itemName,
            String category,
            String pickupAddress,
            String pickupPhoneNumber,
            String deliveryAddress,
            String deliveryPhoneNumber,
            long deliveryFee,
            DeliveryStatus status,
            Instant requestedAt
    ) {
    }
}
