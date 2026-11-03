package setty.delivery.api.dto;

import java.time.Instant;
import setty.delivery.application.readmodel.Shipment;
import setty.delivery.domain.delivery.DeliveryStatus;

public record ShipmentDetailResponse(
        long deliveryId,
        long orderId,
        FurnitureResponse furniture,
        DeliveryPointResponse pickup,
        DeliveryPointResponse destination,
        long deliveryFee,
        Integer estimatedDistanceMeters,
        DeliveryStatus status,
        Instant requestedAt,
        Instant acceptedAt,
        Instant pickedUpAt,
        Instant deliveredAt
) {

    public static ShipmentDetailResponse from(final Shipment.Detail detail, final String thumbnailUrl,
                                               final Integer estimatedDistanceMeters) {
        return new ShipmentDetailResponse(
                detail.deliveryId(),
                detail.orderId(),
                new FurnitureResponse(detail.itemName(), detail.category(), thumbnailUrl),
                new DeliveryPointResponse(detail.pickupAddress(), detail.pickupPhoneNumber()),
                new DeliveryPointResponse(detail.deliveryAddress(), detail.deliveryPhoneNumber()),
                detail.deliveryFee(),
                estimatedDistanceMeters,
                detail.status(),
                detail.requestedAt(),
                detail.acceptedAt(),
                detail.pickedUpAt(),
                detail.deliveredAt()
        );
    }
}
