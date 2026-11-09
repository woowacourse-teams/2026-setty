package setty.payment.domain;

public enum PaymentStatus {

    // 토스 승인을 부르기 전에 남기는 시도. 응답을 끝내 못 받으면 이 상태로 남고 정리 배치가 토스에 다시 묻는다.
    UNKNOWN("승인 확인 중"),
    DONE("결제 완료"),
    CANCELED("환불 완료"),
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
