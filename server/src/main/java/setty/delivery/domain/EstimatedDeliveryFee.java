package setty.delivery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

@Embeddable
public record EstimatedDeliveryFee(
        @Column(name = "estimated_fee", nullable = false) Integer value
) {

    public EstimatedDeliveryFee {
        if (value == null || value < 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }
}
