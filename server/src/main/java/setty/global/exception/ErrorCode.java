package setty.global.exception;

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

    public int getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
