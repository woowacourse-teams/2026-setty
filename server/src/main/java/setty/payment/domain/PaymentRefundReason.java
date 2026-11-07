package setty.payment.domain;

public enum PaymentRefundReason {

    ORDER_EXPIRED("결제 대기 시간이 지나 주문이 만료되어 결제를 취소합니다"),
    ORDER_CANCELLED("주문이 취소되어 결제를 취소합니다");

    // 토스 결제 취소 cancelReason(200자 이하)으로 보낸다.
    private final String cancelReason;

    PaymentRefundReason(final String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public static PaymentRefundReason ofOrderStatus(final String orderStatus) {
        return "EXPIRED".equals(orderStatus) ? ORDER_EXPIRED : ORDER_CANCELLED;
    }

    public String getCancelReason() {
        return cancelReason;
    }
}
