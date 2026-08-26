package setty.delivery.controller.dto;

import java.time.Instant;
import setty.common.DeliveryStatus;
import setty.delivery.query.Shipment;

public record ShipmentDetailResponse(
        long deliveryId,
        long orderId,
        FurnitureResponse furniture,
        DeliveryPointResponse pickup,
        DeliveryPointResponse destination,
        long deliveryFee,
        DeliveryStatus status,
        Instant requestedAt,
        Instant acceptedAt,
        Instant pickedUpAt,
        Instant deliveredAt
) {

    public static ShipmentDetailResponse from(final Shipment.Detail detail) {
        return new ShipmentDetailResponse(
                detail.deliveryId(),
                detail.orderId(),
                new FurnitureResponse(detail.itemName(), detail.category()),
                new DeliveryPointResponse(detail.pickupAddress(), detail.pickupPhoneNumber()),
                new DeliveryPointResponse(detail.deliveryAddress(), detail.deliveryPhoneNumber()),
                detail.deliveryFee(),
                detail.status(),
                detail.requestedAt(),
                detail.acceptedAt(),
                detail.pickedUpAt(),
                detail.deliveredAt()
        );
    }
}
