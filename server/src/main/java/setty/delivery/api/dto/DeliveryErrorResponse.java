package setty.delivery.api.dto;

import setty.global.exception.ErrorCode;

public record DeliveryErrorResponse(
        String code,
        String message
) {

    public static DeliveryErrorResponse from(final ErrorCode errorCode) {
        return new DeliveryErrorResponse(errorCode.name(), errorCode.getMessage());
    }
}
