package setty.common;

/** 주문 영역이 접수한 구매자 취소 요청. 같은 요청을 재전달할 때도 동일한 cancellationRequestId를 사용한다. */
public record OrderCancellationRequested(
        Long orderId,
        String cancellationRequestId
) {
}
