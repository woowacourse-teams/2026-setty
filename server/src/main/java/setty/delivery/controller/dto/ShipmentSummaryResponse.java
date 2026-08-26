package setty.delivery.controller.dto;

import java.time.Instant;
import setty.common.DeliveryStatus;
import setty.delivery.query.Shipment;

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
