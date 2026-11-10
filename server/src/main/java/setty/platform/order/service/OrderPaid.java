package setty.platform.order.service;

/**
 * 주문이 승인을 받아들여 PAID가 됐다. 주문 모듈 안에서만 쓰며, 수신 측이 주문을 확정하고 배송 요청을 발행한다.
 * 발행 기록에 남아 확정이 실패해도 다시 처리된다.
 */
public record OrderPaid(
        Long orderId
) {
}
