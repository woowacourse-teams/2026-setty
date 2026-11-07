package setty.payment.infrastructure;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Set;

/**
 * 토스 결제 승인·조회 응답(Payment 객체) 중 우리가 판단에 쓰는 필드만 담는다.
 * (Spring Boot 기본 Jackson 설정이 나머지 알 수 없는 필드는 무시한다.)
 */
public record TossConfirmResult(
        String paymentKey,
        String orderId,
        String status,
        Integer totalAmount,
        String approvedAt
) {

    // 승인되지 않았거나 이미 돈이 돌아간 결제. 이 결제로는 우리 쪽에 남길 승인이 없다.
    private static final Set<String> NOT_APPROVED = Set.of("ABORTED", "EXPIRED", "CANCELED");

    public boolean isNotApproved() {
        return NOT_APPROVED.contains(status);
    }

    public boolean isDoneFor(final String tossOrderId, final int amount) {
        return "DONE".equals(status) && tossOrderId.equals(orderId) && totalAmount != null && totalAmount == amount;
    }

    public LocalDateTime approvedAtAsLocalDateTime() {
        if (approvedAt == null || approvedAt.isBlank()) {
            return LocalDateTime.now();
        }
        return OffsetDateTime.parse(approvedAt).toLocalDateTime();
    }
}
