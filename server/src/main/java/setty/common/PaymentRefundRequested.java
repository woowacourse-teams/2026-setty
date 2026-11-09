package setty.common;

/**
 * 만료되거나 취소된 주문에 늦게 결제 완료가 도착했을 때 플랫폼(주문) 팀이 발행하고 payment 팀이 수신하는 이벤트.
 * 수신 측은 그 주문의 승인된 결제를 환불 대상으로 기록한다.
 *
 * @param orderStatus 환불 사유를 정할 주문 상태(EXPIRED / CANCELLED)
 */
public record PaymentRefundRequested(
        Long orderId,
        String orderStatus
) {
}
