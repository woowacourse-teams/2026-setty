package setty.settlement;

import java.time.Instant;

public record SettlementSummary(
        Long id,
        Long orderId,
        Long listingId,
        int amount,
        String status,
        Instant createdAt,
        Instant confirmedAt,
        Instant cancelledAt
) {
}
