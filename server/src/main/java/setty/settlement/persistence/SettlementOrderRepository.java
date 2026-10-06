package setty.settlement.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import setty.settlement.domain.SettlementDecision;
import setty.settlement.domain.SettlementStatus;

/**
 * 주문별 정산 결론과, 같은 주문의 정산 처리를 직렬화하는 잠금 행.
 * 정산 행 기록과 결론 반영이 서로 다른 트랜잭션에서 동시에 일어나면 서로의 미커밋 변경을 보지 못해 정산이 PENDING에 남을 수 있다.
 */
@Repository
@RequiredArgsConstructor
public class SettlementOrderRepository {

    private final JdbcTemplate jdbcTemplate;

    // INSERT ... ON DUPLICATE KEY UPDATE는 행을 새로 만들든 이미 있든 배타 락을 잡는다.
    // INSERT IGNORE 뒤 FOR UPDATE는 이미 있는 행에 공유 락을 먼저 잡아 동시 처리 간 교착이 생긴다.
    public Optional<SettlementDecision> lock(final Long orderId) {
        jdbcTemplate.update(
                "INSERT INTO settlement_order (order_id) VALUES (?) ON DUPLICATE KEY UPDATE order_id = order_id",
                orderId
        );
        return jdbcTemplate.query(
                "SELECT outcome, decided_at FROM settlement_order WHERE order_id = ? AND outcome IS NOT NULL",
                (resultSet, rowNumber) -> new SettlementDecision(
                        SettlementStatus.valueOf(resultSet.getString("outcome")),
                        resultSet.getTimestamp("decided_at").toInstant()
                ),
                orderId
        ).stream().findFirst();
    }

    public void decide(final Long orderId, final SettlementDecision decision) {
        jdbcTemplate.update(
                "UPDATE settlement_order SET outcome = ?, decided_at = ? WHERE order_id = ?",
                decision.status().name(),
                Timestamp.from(decision.decidedAt()),
                orderId
        );
    }
}
