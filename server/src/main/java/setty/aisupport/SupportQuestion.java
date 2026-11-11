package setty.aisupport;

import java.time.Instant;
import java.util.Map;

/**
 * Server-owned question identity. The source turn is also its monotonically increasing context
 * version.
 */
record SupportQuestion(
        long sourceTurnId,
        SupportDtos.QuestionKind kind,
        long orderId,
        Instant expiresAt,
        Map<String, Object> search) {
    boolean fresh(Instant now) {
        return expiresAt.isAfter(now);
    }
}
