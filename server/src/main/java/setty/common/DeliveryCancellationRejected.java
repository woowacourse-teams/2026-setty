package setty.common;

import java.time.Instant;

/** 배송이 ACCEPTED 이후 단계에 있어 취소 요청을 거절한 사실. */
public record DeliveryCancellationRejected(
        Long deliveryId,
        Long orderId,
        String cancellationRequestId,
        Instant decidedAt,
        String reasonCode
) {
    public DeliveryCancellationRejected(
            final Long deliveryId,
            final Long orderId,
            final String cancellationRequestId,
            final Instant decidedAt
    ) {
        this(deliveryId, orderId, cancellationRequestId, decidedAt,
                CancellationRejectionReasons.DRIVER_ACCEPTED_OR_LATER);
    }
}
