package setty.payment.domain;

public enum PaymentRefundStatus {

    REQUESTED,
    DONE,
    // 재시도해도 결과가 같거나 재시도 한도를 넘겼다. 사람이 토스 상점관리자에서 확인한다.
    FAILED
}
