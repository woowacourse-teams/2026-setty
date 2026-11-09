package setty.payment.infrastructure;

/** 토스 결제 조회({@code GET /v1/payments/{paymentKey}}) 결과. */
public sealed interface TossPaymentLookup {

    record Found(TossConfirmResult payment) implements TossPaymentLookup {
    }

    // 토스가 이 결제 키를 모른다(404 중 코드가 NOT_FOUND_PAYMENT인 응답만). 승인이 일어나지 않았다.
    record NotFound() implements TossPaymentLookup {
    }

    // 통신 실패·5xx·경로 오류 404 등으로 지금은 알 수 없다.
    record Unavailable() implements TossPaymentLookup {
    }
}
