package setty.settlement.domain;

import java.time.Instant;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

// 주문의 판매 완료(CONFIRMED) 또는 취소(CANCELLED). 주문의 모든 정산이 이 상태로 끝난다.
public record SettlementDecision(
        SettlementStatus status,
        Instant decidedAt
) {

    public SettlementDecision {
        if (status == null || status == SettlementStatus.PENDING || decidedAt == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    public static SettlementDecision completed(final Instant completedAt) {
        return new SettlementDecision(SettlementStatus.CONFIRMED, completedAt);
    }

    public static SettlementDecision cancelled(final Instant cancelledAt) {
        return new SettlementDecision(SettlementStatus.CANCELLED, cancelledAt);
    }
}
