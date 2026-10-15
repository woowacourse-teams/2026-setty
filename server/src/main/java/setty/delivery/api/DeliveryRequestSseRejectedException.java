package setty.delivery.api;

final class DeliveryRequestSseRejectedException extends RuntimeException {

    enum Reason {
        DRIVER_LIMIT_EXCEEDED("기사의 배송 요청 SSE 연결 상한을 초과했습니다."),
        TOTAL_LIMIT_EXCEEDED("서버의 배송 요청 SSE 연결 상한을 초과했습니다."),
        STREAM_CLOSED("배송 요청 SSE 연결 관리가 종료되었습니다.");

        private final String message;

        Reason(final String message) {
            this.message = message;
        }
    }

    private final Reason reason;

    DeliveryRequestSseRejectedException(final Reason reason) {
        super(reason.message);
        this.reason = reason;
    }

    Reason reason() {
        return reason;
    }
}
