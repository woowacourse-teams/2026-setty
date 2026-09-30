package setty.delivery.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import setty.delivery.domain.OrderId;

/**
 * 같은 주문의 배차 요청과 취소 요청을 직렬화한다.
 * 배송과 취소 기록은 서로 다른 테이블이라 UNIQUE 제약만으로는 서로를 막지 못하므로, 주문 하나당 한 줄을 잠근다.
 * 호출한 트랜잭션이 끝날 때까지 락을 유지한다.
 */
@Repository
@RequiredArgsConstructor
public class DeliveryOrderLockRepository {

    private final JdbcTemplate jdbcTemplate;

    public void lock(final OrderId orderId) {
        jdbcTemplate.update("INSERT IGNORE INTO delivery_order_lock (order_id) VALUES (?)", orderId.value());
        jdbcTemplate.queryForObject(
                "SELECT order_id FROM delivery_order_lock WHERE order_id = ? FOR UPDATE",
                Long.class,
                orderId.value()
        );
    }
}
