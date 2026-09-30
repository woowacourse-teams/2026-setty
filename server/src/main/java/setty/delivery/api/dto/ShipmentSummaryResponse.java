package setty.delivery.api.dto;

import java.time.Instant;
import setty.delivery.application.readmodel.Shipment;
import setty.delivery.domain.DeliveryStatus;

public record ShipmentSummaryResponse(
        long deliveryId,
        String itemName,
        String category,
        String pickupAddress,
        String deliveryAddress,
        long deliveryFee,
        DeliveryStatus status,
        Instant acceptedAt
) {

    public static ShipmentSummaryResponse from(final Shipment.Summary summary) {
        return new ShipmentSummaryResponse(
                summary.deliveryId(),
                summary.itemName(),
                summary.category(),
                summary.pickupAddress(),
                summary.deliveryAddress(),
                summary.deliveryFee(),
                summary.status(),
                summary.acceptedAt()
        );
    }
}
