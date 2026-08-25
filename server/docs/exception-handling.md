# 예외 처리 규칙 (원본)

이 문서가 SETTY 서버 예외 처리의 유일한 원본이다.
플랫폼 팀·배송 팀 모두 이 규칙을 따르며, 이 문서와 다른 방식의 예외 처리를 발견하면 이 문서 기준으로 고친다.
규칙 변경은 양 팀 합의 후 이 문서를 먼저 수정하고 코드를 수정한다.

## 1. 예외 클래스는 BusinessException 하나만

```java
// src/main/java/setty/global/exception/BusinessException.java
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
```

- 도메인별 예외 클래스(`OrderException`, `ListingException`, `DeliveryException` 등)를 새로 만들지 않는다.
- `IllegalStateException`, `IllegalArgumentException`, `RuntimeException`을 비즈니스 규칙 위반에 생으로 던지지 않는다. 전부 `BusinessException`으로 던진다.

## 2. 에러 코드는 ErrorCode enum 한 파일에

```java
// src/main/java/setty/global/exception/ErrorCode.java
public enum ErrorCode {

    // ===== 공통 =====
    INVALID_REQUEST(400, "잘못된 요청입니다"),
    INTERNAL_ERROR(500, "서버 오류가 발생했습니다"),

    // ===== 인증 (플랫폼 팀) =====
    INVALID_TOKEN(401, "유효하지 않은 토큰입니다"),
    LOGIN_FAILED(401, "아이디 또는 비밀번호가 일치하지 않습니다"),
    DUPLICATE_LOGIN_ID(400, "이미 사용 중인 아이디입니다"),

    // ===== 매물 (플랫폼 팀) =====
    LISTING_NOT_FOUND(404, "존재하지 않는 매물입니다"),

    // ===== 주문 (플랫폼 팀) =====
    ORDER_NOT_FOUND(404, "존재하지 않는 주문입니다"),
    ALREADY_ORDERED(400, "이미 주문된 매물입니다"),
    CANNOT_ORDER_OWN_LISTING(400, "본인 매물은 주문할 수 없습니다"),

    // ===== 배송 (배송 팀) =====
    DELIVERY_NOT_FOUND(404, "존재하지 않는 배송 요청입니다"),
    DELIVERY_ALREADY_ACCEPTED(400, "이미 다른 기사가 수락했습니다"),
    INVALID_STATUS_TRANSITION(400, "잘못된 배송 상태 변경입니다");

    private final int status;
    private final String message;

    ErrorCode(int status, String message) {
        this.status = status;
        this.message = message;
    }

    public int getStatus() { return status; }
    public String getMessage() { return message; }
}
```

규칙:
- 에러 코드 추가는 **자기 팀 구역 주석 아래에만** 한다. 다른 팀 구역의 코드를 수정·삭제하지 않는다. (사용은 자유)
- message는 **사용자에게 그대로 보여줄 한국어 문장**으로 쓴다.
  - ✅ "존재하지 않는 매물입니다"
  - ❌ "listing not found in db", "잘못된 접근"
- status는 400(비즈니스 위반) / 401(인증) / 404(없음) 중에서 고른다. 새 상태코드가 필요하면 팀에 먼저 알린다.

## 3. GlobalExceptionHandler가 응답 형식을 강제한다

```java
// src/main/java/setty/global/exception/GlobalExceptionHandler.java
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        return ResponseEntity
                .status(code.getStatus())
                .body(new ErrorResponse(code.name(), code.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("잘못된 요청입니다");
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_REQUEST", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("예상치 못한 오류", e);
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse("INTERNAL_ERROR", "서버 오류가 발생했습니다"));
    }
}
```

```java
// src/main/java/setty/global/exception/ErrorResponse.java
public record ErrorResponse(String code, String message) {}
```

- 에러 응답은 항상 `{ "code": "...", "message": "..." }` 형태다. 핸들러가 보장하므로 컨트롤러·서비스에서 에러 응답을 직접 만들지 않는다.
- 500 응답에 스택트레이스나 내부 정보를 담지 않는다. 서버 로그에만 남긴다.

## 4. 사용 규칙

1. 비즈니스 규칙 위반은 무조건 `throw new BusinessException(ErrorCode.XXX)`.
2. 컨트롤러에서 try-catch 하지 않는다. 핸들러가 전부 받는다.
3. 도메인 메서드(엔티티 내부)에서도 `BusinessException`을 던진다.
4. 요청 필드 검증은 DTO에 `@Valid` + Bean Validation 어노테이션으로 한다. 서비스에서 null 체크 코드를 중복으로 만들지 않는다.

```java
// ✅ 옳은 예 — 엔티티 상태 전이
public void accept(Long driverId) {
    if (this.status != DeliveryStatus.REQUESTED) {
        throw new BusinessException(ErrorCode.DELIVERY_ALREADY_ACCEPTED);
    }
    this.status = DeliveryStatus.ACCEPTED;
    this.driverId = driverId;
}

// ✅ 옳은 예 — 서비스 조회
Listing listing = listingRepository.findById(listingId)
        .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND));

// ❌ 틀린 예
throw new IllegalStateException("이미 수락됨");           // 생 예외 → 500으로 떨어짐
throw new DeliveryException("...");                      // 개별 예외 클래스 생성 금지
return ResponseEntity.badRequest().body("이미 주문됨");   // 수동 에러 응답 금지
```

## 5. 클라이언트(앱)와의 약속

성공이 아닌 모든 응답의 몸체는 `{ code, message }`다.
message는 그대로 화면에 띄워도 되는 사용자 언어다.
앱은 code로 분기하고 message는 표시용으로만 쓴다.
