package setty.notification.keyword.domain;

import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

public enum KeywordCategory {
    SOFA,
    TABLE,
    DESK,
    CHAIR,
    STORAGE,
    BED;

    public static KeywordCategory from(final String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return valueOf(name.strip().toUpperCase());
        } catch (final IllegalArgumentException unknown) {
            throw new BusinessException(ErrorCode.INVALID_KEYWORD_CONDITION);
        }
    }
}
