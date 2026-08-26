package setty.delivery.domain;

import jakarta.persistence.Embeddable;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

@Embeddable
public record PhoneNumber(String value) {

    public PhoneNumber {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        value = value.trim();
    }
}
