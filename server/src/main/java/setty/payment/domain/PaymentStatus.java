package setty.payment.domain;

public enum PaymentStatus {

    DONE("결제 완료"),
    // 더 이상 새로 저장하지 않는다(실패는 무기록 + PaymentFailed 발행). 기존 DB 행을 읽기 위한 레거시 상수.
    ABORTED("결제 중단");

    private final String label;

    PaymentStatus(final String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
