package setty.payment.infrastructure;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * 토스 결제 승인·조회 응답(Payment 객체) 중 우리가 판단에 쓰는 필드만 담는다.
 * (Spring Boot 기본 Jackson 설정이 나머지 알 수 없는 필드는 무시한다.)
 */
public record TossConfirmResult(
        String paymentKey,
        String orderId,
        TossPaymentStatus status,
        Integer totalAmount,
        String approvedAt
) {

    public TossConfirmResult {
        // 본문에 status가 없으면 판단할 수 없는 상태로 둔다.
        status = status == null ? TossPaymentStatus.UNKNOWN : status;
    }

    public boolean isNotApproved() {
        return status.isNotApproved();
    }

    public boolean isDone() {
        return status == TossPaymentStatus.DONE;
    }

    public boolean isDoneFor(final String tossOrderId, final int amount) {
        return isDone() && tossOrderId.equals(orderId) && totalAmount != null && totalAmount == amount;
    }

    public LocalDateTime approvedAtAsLocalDateTime() {
        if (approvedAt == null || approvedAt.isBlank()) {
            return LocalDateTime.now();
        }
        return OffsetDateTime.parse(approvedAt).toLocalDateTime();
    }
}
