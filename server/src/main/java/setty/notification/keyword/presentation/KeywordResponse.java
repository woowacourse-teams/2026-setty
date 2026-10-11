package setty.notification.keyword.presentation;

import java.time.Instant;
import setty.notification.keyword.domain.KeywordSubscription;

public record KeywordResponse(Long id, String keyword, Instant createdAt) {

    public static KeywordResponse from(final KeywordSubscription subscription) {
        return new KeywordResponse(subscription.getId(), subscription.getKeyword(), subscription.getCreatedAt());
    }
}
