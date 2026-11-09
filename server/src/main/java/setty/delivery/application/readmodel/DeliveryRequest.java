package setty.delivery.application.readmodel;

import java.time.Instant;
import setty.delivery.domain.delivery.DeliveryStatus;

public final class DeliveryRequest {

    private DeliveryRequest() {
    }

    public record Summary(
            long deliveryId,
            long orderId,
            String itemName,
            String category,
            String pickupAddress,
            String deliveryAddress,
            long deliveryFee,
            DeliveryStatus status,
            Instant requestedAt,
            String pickupDongCode,
            String deliveryDongCode
    ) {
    }

    public record Detail(
            long deliveryId,
            long orderId,
            String itemName,
            String category,
            String pickupAddress,
            String deliveryAddress,
            long deliveryFee,
            DeliveryStatus status,
            Instant requestedAt,
            String pickupDongCode,
            String deliveryDongCode
    ) {
    }

    /** 칸 캐시에 담는 대기 요청. 출발·도착 칸이 모두 있는 요청만 만든다. */
    public record CellCall(int pickupCell, int deliveryCell, Summary summary) {
    }
}
