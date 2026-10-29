package setty.notification.keyword.domain;

import java.util.ArrayList;
import java.util.List;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

public record KeywordConditions(
        KeywordCategory category,
        Integer minPrice,
        Integer maxPrice,
        Integer maxDeliveryFee,
        Integer maxTotalPrice,
        List<String> excludeKeywords
) {

    public static final int MAXIMUM_AMOUNT = 100_000_000;
    public static final int MAXIMUM_EXCLUDE_KEYWORDS = 10;

    public static final KeywordConditions NONE = new KeywordConditions(null, null, null, null, null, List.of());

    public KeywordConditions {
        validateAmount(minPrice);
        validateAmount(maxPrice);
        validateAmount(maxDeliveryFee);
        validateAmount(maxTotalPrice);
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw new BusinessException(ErrorCode.INVALID_KEYWORD_CONDITION);
        }
        excludeKeywords = normalizeExcludeKeywords(excludeKeywords);
    }

    public static KeywordConditions of(
            final String category,
            final Integer minPrice,
            final Integer maxPrice,
            final Integer maxDeliveryFee,
            final Integer maxTotalPrice,
            final List<String> excludeKeywords
    ) {
        return new KeywordConditions(
                KeywordCategory.from(category), minPrice, maxPrice, maxDeliveryFee, maxTotalPrice, excludeKeywords);
    }

    public boolean dependsOnDeliveryFee() {
        return maxDeliveryFee != null || maxTotalPrice != null;
    }

    private static void validateAmount(final Integer amount) {
        if (amount != null && (amount < 0 || amount > MAXIMUM_AMOUNT)) {
            throw new BusinessException(ErrorCode.INVALID_KEYWORD_CONDITION);
        }
    }

    // 공백만 있는 항목은 버리고, 대소문자만 다른 중복은 하나로 본다.
    private static List<String> normalizeExcludeKeywords(final List<String> excludeKeywords) {
        final List<String> normalized = new ArrayList<>();
        for (final String word : excludeKeywords == null ? List.<String>of() : excludeKeywords) {
            if (word == null || word.isBlank()) {
                continue;
            }
            final String stripped = word.strip();
            if (stripped.length() > KeywordSubscription.MAXIMUM_KEYWORD_LENGTH) {
                throw new BusinessException(ErrorCode.INVALID_KEYWORD_CONDITION);
            }
            if (normalized.stream().noneMatch(stripped::equalsIgnoreCase)) {
                normalized.add(stripped);
            }
        }
        if (normalized.size() > MAXIMUM_EXCLUDE_KEYWORDS) {
            throw new BusinessException(ErrorCode.INVALID_KEYWORD_CONDITION);
        }
        return List.copyOf(normalized);
    }
}
