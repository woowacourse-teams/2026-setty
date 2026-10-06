package setty.common;

import java.time.Instant;

/** 구매자 확인 또는 배송 완료 후 기한 경과로 판매 완료가 확정된 사실. 판매자·기사 정산 확정에 사용한다. */
public record OrderCompleted(
        Long orderId,
        Instant completedAt
) {
}
