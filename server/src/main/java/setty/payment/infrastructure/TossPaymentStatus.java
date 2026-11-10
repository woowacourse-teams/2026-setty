package setty.payment.infrastructure;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Arrays;

/**
 * 토스 결제 객체의 status. 토스 문자열을 이 enum으로 번역해 바깥에서는 문자열을 비교하지 않는다.
 * 모르는 값(토스가 새로 추가한 상태 등)이나 빈 값은 UNKNOWN으로 두어, 승인으로도 미승인으로도 판단하지 않는다.
 */
public enum TossPaymentStatus {

    READY,
    IN_PROGRESS,
    WAITING_FOR_DEPOSIT,
    DONE,
    CANCELED,
    PARTIAL_CANCELED,
    ABORTED,
    EXPIRED,
    UNKNOWN;

    @JsonCreator
    public static TossPaymentStatus from(final String raw) {
        return Arrays.stream(values())
                .filter(status -> status != UNKNOWN && status.name().equals(raw))
                .findFirst()
                .orElse(UNKNOWN);
    }

    // 승인되지 않았거나 이미 돈이 돌아간 결제. 이 결제로는 우리 쪽에 남길 승인이 없다.
    public boolean isNotApproved() {
        return this == ABORTED || this == EXPIRED || this == CANCELED;
    }
}
