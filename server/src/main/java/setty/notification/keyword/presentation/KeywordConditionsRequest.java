package setty.notification.keyword.presentation;

import java.util.List;
import setty.notification.keyword.domain.KeywordConditions;

public record KeywordConditionsRequest(
        String category,
        Integer minPrice,
        Integer maxPrice,
        Integer maxDeliveryFee,
        Integer maxTotalPrice,
        List<String> excludeKeywords
) {

    public KeywordConditions toConditions() {
        return KeywordConditions.of(category, minPrice, maxPrice, maxDeliveryFee, maxTotalPrice, excludeKeywords);
    }
}
