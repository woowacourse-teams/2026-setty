package setty.common;

import java.time.Instant;

/** 구매자 주문 취소가 확정된 사실. 결제의 환불 대상 기록과 매물 선점 해제에 사용한다. */
public record OrderCancelled(
        Long orderId,
        Long listingId,
        String cancellationRequestId,
        Instant cancelledAt
) {
}
