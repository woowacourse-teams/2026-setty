package setty.platform;

/**
 * 주문이 승인을 받아들였는지에 대한 결과.
 *
 * @param accepted    받아들였으면 true. 결제를 그대로 기록한다.
 * @param orderStatus 받아들이지 않았을 때 주문이 끝난 상태(EXPIRED / CANCELLED). 환불 사유를 정한다.
 */
public record PaymentAcceptance(
        boolean accepted,
        String orderStatus
) {

    public static PaymentAcceptance ofAccepted() {
        return new PaymentAcceptance(true, null);
    }

    public static PaymentAcceptance ofRejected(final String orderStatus) {
        return new PaymentAcceptance(false, orderStatus);
    }
}
