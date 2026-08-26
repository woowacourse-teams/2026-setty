package setty.delivery.domain;

import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

public record DeliveryId(Long value) {

    public DeliveryId {
        if (value == null || value <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }
}
