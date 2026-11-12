package setty.common;

import java.time.Instant;

public record SettlementConfirmed(
        Long orderId,
        Long listingId,
        Long sellerId,
        int amount,
        Instant confirmedAt
) {
}
