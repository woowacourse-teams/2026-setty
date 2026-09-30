package setty.common;

import java.time.Instant;

/** 배송 취소에 성공한 사실. 배송 요청이 생성되지 않았다면 deliveryId는 null이다. */
public record DeliveryCancelled(
        Long deliveryId,
        Long orderId,
        String cancellationRequestId,
        Instant decidedAt) {
}
