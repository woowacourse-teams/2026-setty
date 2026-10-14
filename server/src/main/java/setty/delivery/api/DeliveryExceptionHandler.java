package setty.delivery.api;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import setty.delivery.api.dto.DeliveryErrorResponse;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = DeliveryController.class)
public class DeliveryExceptionHandler {

    @ExceptionHandler(DeliveryRequestSseRejectedException.class)
    public ResponseEntity<DeliveryErrorResponse> handleSseRejection(final DeliveryRequestSseRejectedException exception) {
        final HttpStatus status = exception.reason() == DeliveryRequestSseRejectedException.Reason.DRIVER_LIMIT_EXCEEDED
                ? HttpStatus.TOO_MANY_REQUESTS
                : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new DeliveryErrorResponse(exception.reason().name(), exception.getMessage()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<DeliveryErrorResponse> handleBusiness(final BusinessException exception) {
        final ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity
                .status(errorCode.getStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(DeliveryErrorResponse.from(errorCode));
    }
}
