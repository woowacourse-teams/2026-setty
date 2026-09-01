package setty.payment.domain;

public enum PaymentStatus {

    DONE("결제 완료"),
    ABORTED("결제 중단");

    private final String label;

    PaymentStatus(final String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
