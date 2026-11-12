package setty.platform;

/** 서버의 최종 동의 검증을 통과한 요청만 사용하는 주문 변경 경계. 모델 도구가 아니다. */
public interface OrderSupportCancellation {
    Outcome request(long buyerId, long orderId);
    record Outcome(OrderSupportReader.Context context, String requestId) {
        public boolean accepted() { return requestId != null; }
    }
}
