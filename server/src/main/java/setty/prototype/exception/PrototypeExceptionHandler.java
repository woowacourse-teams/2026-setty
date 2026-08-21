package setty.prototype.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * 프로토타입 API만 계약 문서의 {@code code}·{@code message} 오류 형식을 쓴다.
 * 기존 견적·배차 API의 오류 형식을 바꾸지 않도록 {@code setty.prototype} 컨트롤러로 범위를 제한하고,
 * 같은 예외를 다루는 기존 처리기보다 먼저 적용되도록 우선순위를 가장 높게 둔다.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "setty.prototype")
public class PrototypeExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(PrototypeExceptionHandler.class);
    private static final String INVALID_REQUEST_MESSAGE = "요청 값이 올바르지 않습니다.";

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<PrototypeErrorResponse> handleInvalidCredentials(
            final InvalidCredentialsException exception
    ) {
        return response(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", exception.getMessage());
    }

    @ExceptionHandler(AuthenticationRequiredException.class)
    public ResponseEntity<PrototypeErrorResponse> handleAuthenticationRequired(
            final AuthenticationRequiredException exception
    ) {
        return response(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", exception.getMessage());
    }

    @ExceptionHandler(ListingAccessDeniedException.class)
    public ResponseEntity<PrototypeErrorResponse> handleListingAccessDenied(
            final ListingAccessDeniedException exception
    ) {
        return response(HttpStatus.FORBIDDEN, "LISTING_ACCESS_DENIED", exception.getMessage());
    }

    @ExceptionHandler(ListingNotFoundException.class)
    public ResponseEntity<PrototypeErrorResponse> handleListingNotFound(final ListingNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "LISTING_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(InvalidImageCountException.class)
    public ResponseEntity<PrototypeErrorResponse> handleInvalidImageCount(final InvalidImageCountException exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_IMAGE_COUNT", exception.getMessage());
    }

    @ExceptionHandler(UnsupportedImageTypeException.class)
    public ResponseEntity<PrototypeErrorResponse> handleUnsupportedImageType(
            final UnsupportedImageTypeException exception
    ) {
        return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_IMAGE_TYPE", exception.getMessage());
    }

    @ExceptionHandler(ImagePayloadTooLargeException.class)
    public ResponseEntity<PrototypeErrorResponse> handleImagePayloadTooLarge(
            final ImagePayloadTooLargeException exception
    ) {
        return response(HttpStatus.CONTENT_TOO_LARGE, "PAYLOAD_TOO_LARGE", exception.getMessage());
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<PrototypeErrorResponse> handleInvalidRequest(final InvalidRequestException exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage());
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestPartException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<PrototypeErrorResponse> handleInvalidInput() {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", INVALID_REQUEST_MESSAGE);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<PrototypeErrorResponse> handleDatabaseError(final DataAccessException exception) {
        log.error("프로토타입 요청 처리 중 데이터베이스 오류가 났다.", exception);

        return internalServerError();
    }

    /**
     * S3 업로드 실패처럼 예상하지 못한 실패도 계약 형식으로 응답한다.
     * 더 구체적인 처리기가 있는 예외는 그쪽이 먼저 잡는다.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<PrototypeErrorResponse> handleUnexpectedError(final RuntimeException exception) {
        log.error("프로토타입 요청을 처리하지 못했다.", exception);

        return internalServerError();
    }

    private ResponseEntity<PrototypeErrorResponse> internalServerError() {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "요청을 처리하지 못했습니다.");
    }

    private ResponseEntity<PrototypeErrorResponse> response(
            final HttpStatus status,
            final String code,
            final String message
    ) {
        return ResponseEntity.status(status).body(new PrototypeErrorResponse(code, message));
    }
}
