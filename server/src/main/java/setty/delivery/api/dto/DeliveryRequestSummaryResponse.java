package setty.delivery.api.dto;

import java.time.Instant;
import setty.delivery.application.readmodel.DeliveryRequest;
import setty.delivery.domain.delivery.DeliveryStatus;

public record DeliveryRequestSummaryResponse(
        long deliveryId,
        String itemName,
        String category,
        String thumbnailUrl,
        String pickupAddress,
        String deliveryAddress,
        long deliveryFee,
        DeliveryStatus status,
        Instant requestedAt
) {

    public static DeliveryRequestSummaryResponse from(final DeliveryRequest.Summary summary, final String thumbnailUrl) {
        return new DeliveryRequestSummaryResponse(
                summary.deliveryId(),
                summary.itemName(),
                summary.category(),
                thumbnailUrl,
                summary.pickupAddress(),
                summary.deliveryAddress(),
                summary.deliveryFee(),
                summary.status(),
                summary.requestedAt()
        );
    }
}
