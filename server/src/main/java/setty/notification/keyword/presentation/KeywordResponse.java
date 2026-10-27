package setty.notification.keyword.presentation;

import java.time.Instant;
import java.util.List;
import setty.notification.keyword.domain.KeywordConditions;
import setty.notification.keyword.domain.KeywordSubscription;

public record KeywordResponse(
        Long id,
        String keyword,
        String category,
        Integer minPrice,
        Integer maxPrice,
        Integer maxDeliveryFee,
        Integer maxTotalPrice,
        List<String> excludeKeywords,
        Instant createdAt
) {

    public static KeywordResponse from(final KeywordSubscription subscription) {
        final KeywordConditions conditions = subscription.getConditions();
        return new KeywordResponse(
                subscription.getId(),
                subscription.getKeyword(),
                conditions.category() == null ? null : conditions.category().name(),
                conditions.minPrice(),
                conditions.maxPrice(),
                conditions.maxDeliveryFee(),
                conditions.maxTotalPrice(),
                conditions.excludeKeywords(),
                subscription.getCreatedAt()
        );
    }
}
