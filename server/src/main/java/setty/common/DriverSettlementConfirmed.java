package setty.common;

import java.time.Instant;

// 기사 몫 정산이 확정된 사실. 판매자 몫은 SettlementConfirmed로 따로 낸다. 기사 정산에는 매물이 없다.
public record DriverSettlementConfirmed(
        Long orderId,
        Long driverId,
        int amount,
        Instant confirmedAt
) {
}
