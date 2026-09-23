package setty.payment.infrastructure;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * 토스 결제 승인 응답 중 우리가 저장에 사용하는 필드만 담는다.
 * (Spring Boot 기본 Jackson 설정이 나머지 알 수 없는 필드는 무시한다.)
 */
public record TossConfirmResult(
        String paymentKey,
        String orderId,
        String status,
        Integer totalAmount,
        String approvedAt
) {

    public LocalDateTime approvedAtAsLocalDateTime() {
        if (approvedAt == null || approvedAt.isBlank()) {
            return LocalDateTime.now();
        }
        return OffsetDateTime.parse(approvedAt).toLocalDateTime();
    }
}
