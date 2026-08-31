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
    LISTING_UPDATE_NOT_ALLOWED(400, "구매 신청이 있거나 거래가 시작된 매물은 수정할 수 없습니다"),
    LISTING_DELETE_NOT_ALLOWED(400, "구매 신청이 있거나 거래가 시작된 매물은 삭제할 수 없습니다"),
    LISTING_NOT_AVAILABLE(400, "구매 신청을 받을 수 없는 매물입니다"),
    INVALID_LISTING_STATUS_TRANSITION(400, "매물 상태를 변경할 수 없습니다"),
    INVALID_LISTING_IMAGE_COUNT(400, "매물 사진은 1장 이상 5장 이하로 등록해야 합니다"),
    LISTING_IMAGE_TOO_LARGE(400, "매물 사진 전체 용량은 25MB 이하여야 합니다"),
    UNSUPPORTED_LISTING_IMAGE_TYPE(400, "JPEG, PNG, WebP 사진만 등록할 수 있습니다"),
    INVALID_LISTING_IMAGE_REFERENCE(400, "유효하지 않은 매물 사진입니다"),

    // ===== 주문 (플랫폼 팀) =====
    ORDER_NOT_FOUND(404, "존재하지 않는 주문입니다"),
    ALREADY_ORDERED(400, "이미 주문된 매물입니다"),
    CANNOT_ORDER_OWN_LISTING(400, "본인 매물은 주문할 수 없습니다"),

    // ===== 찜 (플랫폼 팀) =====
    CANNOT_FAVORITE_OWN_LISTING(400, "본인 매물은 찜할 수 없습니다"),

    // ===== 결제 (payment) =====
    PAYMENT_AMOUNT_MISMATCH(400, "결제 금액이 주문 금액과 일치하지 않습니다"),
    PAYMENT_CONFIRM_FAILED(400, "결제 승인에 실패했습니다"),
    ALREADY_PAID(400, "이미 결제된 주문입니다"),

    // ===== 배송 (배송 팀) =====
    DELIVERY_NOT_FOUND(404, "존재하지 않는 배송 요청입니다"),
    DELIVERY_ALREADY_ACCEPTED(409, "이미 다른 기사가 수락했습니다"),
    INVALID_DELIVERY_TRANSITION(409, "잘못된 배송 상태 변경입니다"),
    DELIVERY_DRIVER_MISMATCH(404, "담당 기사에게 배정된 배송이 아닙니다"),
    ORDER_DELIVERY_STATUS_MISMATCH(409, "주문과 배송 상태가 일치하지 않습니다"),
    DRIVER_ACCESS_DENIED(403, "기사 권한이 필요합니다");

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
