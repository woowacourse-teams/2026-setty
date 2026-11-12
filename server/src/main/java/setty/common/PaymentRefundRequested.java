package setty.common;

/**
 * 만료되거나 취소된 주문에 늦게 결제 완료가 도착했을 때 플랫폼(주문) 팀이 발행하던 이벤트. 지금은 발행하지 않고,
 * 이전 버전이 남긴 발행 기록을 처리하려고 수신자만 남겨 둔다. 끝난 주문의 환불 대상은 결제 기록과 함께 저장한다.
 * 수신 측은 그 주문의 승인된 결제를 환불 대상으로 기록한다.
 *
 * @param orderStatus 환불 사유를 정할 주문 상태(EXPIRED / CANCELLED)
 */
public record PaymentRefundRequested(
        Long orderId,
        String orderStatus
) {
}
