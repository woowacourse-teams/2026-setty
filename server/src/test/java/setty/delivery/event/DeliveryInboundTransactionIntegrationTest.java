package setty.delivery.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static setty.global.event.EventPublicationTestSupport.awaitEventsHandled;
import static setty.global.event.EventPublicationTestSupport.incompletePublicationCount;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import setty.common.OrderCancellationRequested;
import setty.common.OrderConfirmed;
import setty.support.MySqlIntegrationTestSupport;

@SpringBootTest
class DeliveryInboundTransactionIntegrationTest extends MySqlIntegrationTestSupport {

    private static final long ORDER_ID = 101L;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM delivery");
        jdbcTemplate.update("DELETE FROM delivery_order_decision");
        jdbcTemplate.update("DELETE FROM EVENT_PUBLICATION");
    }

    @Test
    void orderConfirmedIsHandledAfterPublisherCommits() {
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            eventPublisher.publishEvent(orderConfirmed());

            assertThat(deliveryCount()).isZero();
        });

        awaitEventsHandled(jdbcTemplate);
        assertThat(deliveryCount()).isOne();
    }

    @Test
    void orderConfirmedIsIgnoredWhenPublisherRollsBack() {
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            eventPublisher.publishEvent(orderConfirmed());
            transaction.setRollbackOnly();
        });

        awaitEventsHandled(jdbcTemplate);
        assertThat(deliveryCount()).isZero();
    }

    @Test
    void deliveryFailureDoesNotEscapeOrRollBackPublisher() {
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction ->
                eventPublisher.publishEvent(orderConfirmed()));
        awaitEventsHandled(jdbcTemplate);

        assertThatCode(() -> new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            jdbcTemplate.update("UPDATE delivery SET category = 'TABLE' WHERE order_id = ?", ORDER_ID);
            eventPublisher.publishEvent(new OrderCancellationRequested(ORDER_ID, " "));
        })).doesNotThrowAnyException();
        awaitEventsHandled(jdbcTemplate);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT category FROM delivery WHERE order_id = ?", String.class, ORDER_ID
        )).isEqualTo("TABLE");
        assertThat(deliveryStatus()).isEqualTo("REQUESTED");
        // 실패한 이벤트는 발행 기록에 남아 재발행 대상이 된다.
        assertThat(incompletePublicationCount(jdbcTemplate, OrderCancellationRequested.class)).isOne();
    }

    private long deliveryCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM delivery", Long.class);
    }

    private String deliveryStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM delivery WHERE order_id = ?",
                String.class,
                ORDER_ID
        );
    }

    private static OrderConfirmed orderConfirmed() {
        return new OrderConfirmed(
                ORDER_ID,
                "가상 원목 의자",
                "CHAIR",
                "서울시 가상구 출발로 1",
                "서울시 가상구 도착로 2",
                10_000,
                "010-0000-0001",
                "010-0000-0002",
                10L,
                1L,
                150_000
        );
    }
}
