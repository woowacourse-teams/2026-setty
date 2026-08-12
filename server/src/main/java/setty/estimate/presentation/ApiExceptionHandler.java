package setty.estimate.presentation;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import setty.estimate.application.exception.EstimateRequestNotFoundException;
import setty.estimate.domain.InvalidEstimateRequestStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidation(final MethodArgumentNotValidException exception) {
        final Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }

        return ResponseEntity.badRequest()
                .body(new ValidationErrorResponse("INVALID_INPUT", "입력값을 확인해 주세요.", fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ValidationErrorResponse> handleUnreadableBody() {
        return ResponseEntity.badRequest()
                .body(new ValidationErrorResponse("INVALID_INPUT", "입력값을 확인해 주세요.", Map.of()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ServerErrorResponse> handleDatabaseError() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ServerErrorResponse("INTERNAL_SERVER_ERROR", "요청을 처리하지 못했습니다."));
    }

    @ExceptionHandler(EstimateRequestNotFoundException.class)
    public ResponseEntity<ServerErrorResponse> handleEstimateRequestNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ServerErrorResponse("ESTIMATE_REQUEST_NOT_FOUND", "견적 요청을 찾을 수 없습니다."));
    }

    @ExceptionHandler(InvalidEstimateRequestStatusException.class)
    public ResponseEntity<ServerErrorResponse> handleInvalidEstimateRequestStatus() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ServerErrorResponse(
                        "INVALID_ESTIMATE_REQUEST_STATUS",
                        "검토 대기 상태의 요청만 견적 안내를 완료할 수 있습니다."
                ));
    }

    public record ValidationErrorResponse(
            String code,
            String message,
            Map<String, String> fieldErrors
    ) {
    }

    public record ServerErrorResponse(
            String code,
            String message
    ) {
    }
}
