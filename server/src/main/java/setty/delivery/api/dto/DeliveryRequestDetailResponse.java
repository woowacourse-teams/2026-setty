package setty.delivery.api.dto;

import java.time.Instant;
import setty.common.DeliveryStatus;
import setty.delivery.application.query.DeliveryRequest;

public record DeliveryRequestDetailResponse(
        long deliveryId,
        long orderId,
        FurnitureResponse furniture,
        DeliveryPointResponse pickup,
        DeliveryPointResponse destination,
        long deliveryFee,
        DeliveryStatus status,
        Instant requestedAt
) {

    public static DeliveryRequestDetailResponse from(final DeliveryRequest.Detail detail) {
        return new DeliveryRequestDetailResponse(
                detail.deliveryId(),
                detail.orderId(),
                new FurnitureResponse(detail.itemName(), detail.category()),
                new DeliveryPointResponse(detail.pickupAddress(), detail.pickupPhoneNumber()),
                new DeliveryPointResponse(detail.deliveryAddress(), detail.deliveryPhoneNumber()),
                detail.deliveryFee(),
                detail.status(),
                detail.requestedAt()
        );
    }
}
