package setty.delivery.api;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import setty.delivery.api.dto.DeliveryErrorResponse;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = DeliveryController.class)
public class DeliveryExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<DeliveryErrorResponse> handleBusiness(final BusinessException exception) {
        final ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(DeliveryErrorResponse.from(errorCode));
    }
}
