package setty.common;

/**
 * 결제가 실패·중단되었을 때 payment 팀이 발행하는 이벤트. 현재 직접 수신자는 없다.
 * 실패 복귀 사실을 알린다. PENDING 주문과 매물 선점은 원래 만료 시각까지 유지한다.
 */
public record PaymentFailed(
        Long orderId
) {
}
