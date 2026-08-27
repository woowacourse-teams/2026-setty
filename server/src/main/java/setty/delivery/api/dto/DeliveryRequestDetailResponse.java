package setty.delivery.api.dto;

import java.time.Instant;
import setty.common.DeliveryStatus;
import setty.delivery.application.query.DeliveryRequest;

public record DeliveryRequestDetailResponse(
        long deliveryId,
        long orderId,
        FurnitureResponse furniture,
        String pickupAddress,
        String destinationAddress,
        long deliveryFee,
        DeliveryStatus status,
        Instant requestedAt
) {

    public static DeliveryRequestDetailResponse from(final DeliveryRequest.Detail detail) {
        return new DeliveryRequestDetailResponse(
                detail.deliveryId(),
                detail.orderId(),
                new FurnitureResponse(detail.itemName(), detail.category()),
                detail.pickupAddress(),
                detail.deliveryAddress(),
                detail.deliveryFee(),
                detail.status(),
                detail.requestedAt()
        );
    }
}
