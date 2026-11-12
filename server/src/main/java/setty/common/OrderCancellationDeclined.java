package setty.common;

import java.time.Instant;

/** A specific cancellation was rejected and the order transition committed. */
public record OrderCancellationDeclined(Long orderId, String cancellationRequestId, Instant decidedAt, String reasonCode) {}
