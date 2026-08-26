package setty.delivery.api.dto;

import java.time.Instant;
import setty.common.DeliveryStatus;
import setty.delivery.application.query.DeliveryRequest;

public record DeliveryRequestSummaryResponse(
        long deliveryId,
        String itemName,
        String category,
        String pickupAddress,
        String deliveryAddress,
        long deliveryFee,
        DeliveryStatus status,
        Instant requestedAt
) {

    public static DeliveryRequestSummaryResponse from(final DeliveryRequest.Summary summary) {
        return new DeliveryRequestSummaryResponse(
                summary.deliveryId(),
                summary.itemName(),
                summary.category(),
                summary.pickupAddress(),
                summary.deliveryAddress(),
                summary.deliveryFee(),
                summary.status(),
                summary.requestedAt()
        );
    }
}
