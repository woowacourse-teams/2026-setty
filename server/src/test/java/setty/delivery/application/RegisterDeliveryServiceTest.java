package setty.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static setty.global.event.EventPublicationTestSupport.awaitEventsHandled;

import java.sql.Timestamp;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import setty.common.OrderConfirmed;
import setty.support.MySqlIntegrationTestSupport;

@SpringBootTest
class RegisterDeliveryServiceTest extends MySqlIntegrationTestSupport {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM EVENT_PUBLICATION");
        jdbcTemplate.update("DELETE FROM delivery");
        jdbcTemplate.update("DELETE FROM delivery_order_decision");
    }

    @Test
    void orderConfirmedEventCreatesRequestedDeliveryWithMatchingData() {
        publishCommitted(orderConfirmed(101L));

        final Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM delivery");
        assertThat(row.get("order_id")).isEqualTo(101L);
        assertThat(row.get("item_name")).isEqualTo("가상 원목 의자");
        assertThat(row.get("category")).isEqualTo("CHAIR");
        assertThat(row.get("pickup_address")).isEqualTo("서울시 가상구 출발로 1");
        assertThat(row.get("delivery_address")).isEqualTo("서울시 가상구 도착로 2");
        assertThat(row.get("pickup_phone_number")).isEqualTo("010-0000-0001");
        assertThat(row.get("delivery_phone_number")).isEqualTo("010-0000-0002");
        assertThat(row.get("estimated_fee")).isEqualTo(10_000);
        assertThat(row.get("status")).isEqualTo("REQUESTED");
        assertThat(row.get("requested_at")).isNotNull();
        assertThat(row.get("driver_id")).isNull();
    }

    @Test
    void duplicatedOrderConfirmedEventCreatesOneDelivery() {
        final OrderConfirmed event = orderConfirmed(202L);

        publishCommitted(event);
        publishCommitted(event);

        assertThat(deliveryCount()).isEqualTo(1L);
    }

    @Test
    void databaseRejectsDuplicatedOrderId() {
        insertDelivery(303L, "첫 번째 가상 의자");

        assertThatThrownBy(() -> insertDelivery(303L, "두 번째 가상 의자"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(deliveryCount()).isEqualTo(1L);
    }

    private void insertDelivery(final long orderId, final String itemName) {
        jdbcTemplate.update(
                """
                INSERT INTO delivery (
                    order_id, item_name, category,
                    pickup_address, delivery_address,
                    pickup_phone_number, delivery_phone_number,
                    estimated_fee, status, requested_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                orderId,
                itemName,
                "CHAIR",
                "서울시 가상구 출발로 1",
                "서울시 가상구 도착로 2",
                "010-0000-0001",
                "010-0000-0002",
                10_000,
                "REQUESTED",
                Timestamp.valueOf("2026-08-26 10:00:00")
        );
    }

    // 모듈 간 이벤트는 발행한 쪽이 커밋된 뒤 비동기로 전달되므로 처리가 끝날 때까지 기다린다.
    private void publishCommitted(final Object event) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> eventPublisher.publishEvent(event));
        awaitEventsHandled(jdbcTemplate);
    }

    private long deliveryCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM delivery", Long.class);
    }

    private static OrderConfirmed orderConfirmed(final long orderId) {
        return new OrderConfirmed(
                orderId,
                "가상 원목 의자",
                "CHAIR",
                "서울시 가상구 출발로 1",
                "서울시 가상구 도착로 2",
                10_000,
                "010-0000-0001",
                "010-0000-0002"
        );
    }
}
