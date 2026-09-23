package setty.common;

/**
 * 결제가 실패·중단되었을 때 payment 팀이 발행하고 플랫폼(주문) 팀이 수신하는 이벤트.
 * 수신 측은 이 orderId의 PENDING 주문을 취소(선점 해제)한다.
 */
public record PaymentFailed(
        Long orderId
) {
}
