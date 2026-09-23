package setty.common;

/**
 * 결제 승인이 확정되었을 때 payment 팀이 발행하고 플랫폼(주문) 팀이 수신하는 이벤트.
 * 수신 측은 이 orderId의 PENDING 주문을 REQUESTED로 전이하고 배차(OrderRequested)를 발행한다.
 */
public record PaymentCompleted(
        Long orderId
) {
}
