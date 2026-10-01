package setty.delivery.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import setty.delivery.domain.OrderId;

/**
 * 주문별 배송 판정. 배송 요청 등록과 구매자 취소 중 같은 주문 키에 먼저 INSERT한 쪽이 결론을 정한다.
 * 다른 트랜잭션이 같은 키를 넣고 아직 커밋하지 않았다면 InnoDB가 그 트랜잭션이 끝날 때까지 기다리게 하므로 별도 락이 필요 없다.
 */
@Repository
@RequiredArgsConstructor
public class DeliveryOrderDecisionRepository {

    private final JdbcTemplate jdbcTemplate;

    // 이 호출이 판정을 정했으면 true, 이미 판정된 주문이면 false.
    public boolean decideRequested(final OrderId orderId, final Instant decidedAt) {
        return decide(orderId, "REQUESTED", decidedAt);
    }

    public boolean decideCancelled(final OrderId orderId, final Instant decidedAt) {
        return decide(orderId, "CANCELLED", decidedAt);
    }

    private boolean decide(final OrderId orderId, final String outcome, final Instant decidedAt) {
        return jdbcTemplate.update(
                "INSERT IGNORE INTO delivery_order_decision (order_id, outcome, decided_at) VALUES (?, ?, ?)",
                orderId.value(),
                outcome,
                Timestamp.from(decidedAt)
        ) == 1;
    }
}
