package setty.delivery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

@Embeddable
public record OrderId(
        @Column(name = "order_id", nullable = false)
        Long value
) {

    public OrderId {
        if (value == null || value <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }
}
