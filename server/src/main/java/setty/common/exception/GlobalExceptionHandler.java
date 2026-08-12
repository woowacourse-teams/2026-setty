package setty.common.exception;

import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import setty.common.operator.UnauthorizedOperatorException;
import setty.dispatch.exception.DispatchRequestNotFoundException;
import setty.dispatch.exception.DispatchStatusTransitionException;
import setty.dispatch.exception.InvalidItemImageException;
import setty.dispatch.exception.SellerInputAlreadySubmittedException;
import setty.dispatch.exception.SellerInputSessionNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(UnauthorizedOperatorException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedOperator(final UnauthorizedOperatorException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler({DispatchRequestNotFoundException.class, SellerInputSessionNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(final RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler({SellerInputAlreadySubmittedException.class, DispatchStatusTransitionException.class})
    public ResponseEntity<ErrorResponse> handleConflict(final RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(final MethodArgumentNotValidException exception) {
        final String invalidFields = exception.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getField)
                .distinct()
                .collect(Collectors.joining(", "));

        return ResponseEntity.badRequest()
                .body(new ErrorResponse("입력값이 올바르지 않습니다: " + invalidFields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(final HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse("요청 본문을 읽을 수 없습니다."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(final MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("요청 값이 올바르지 않습니다: " + exception.getName()));
    }

    @ExceptionHandler(InvalidItemImageException.class)
    public ResponseEntity<ErrorResponse> handleInvalidItemImage(final InvalidItemImageException exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded() {
        return ResponseEntity.badRequest().body(new ErrorResponse("물품 사진은 10MB 이하만 올릴 수 있습니다."));
    }
}
