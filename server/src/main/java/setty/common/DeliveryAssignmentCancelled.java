package setty.common;

import java.time.Instant;

// acceptedAt은 취소된 배차의 수락 시각이다. 주문은 이 값으로 늦게 도착한 취소가 새 배차를 되돌리지 않게 가린다.
public record DeliveryAssignmentCancelled(
        Long deliveryId,
        Long orderId,
        Long driverId,
        Instant acceptedAt,
        Instant changedAt
) {
}
