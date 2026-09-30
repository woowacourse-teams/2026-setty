package setty.delivery.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import setty.common.OrderCancellationRequested;
import setty.common.OrderConfirmed;

@SpringBootTest
@Testcontainers
class DeliveryInboundTransactionIntegrationTest {

    private static final long ORDER_ID = 101L;

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.11")
            .withDatabaseName("setty_test")
            .withUsername("setty_test")
            .withPassword("setty_test");

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM delivery_cancellation");
        jdbcTemplate.update("DELETE FROM delivery");
    }

    @Test
    void orderConfirmedIsHandledAfterPublisherCommits() {
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            eventPublisher.publishEvent(orderConfirmed());

            assertThat(deliveryCount()).isZero();
        });

        assertThat(deliveryCount()).isOne();
    }

    @Test
    void orderConfirmedIsIgnoredWhenPublisherRollsBack() {
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            eventPublisher.publishEvent(orderConfirmed());
            transaction.setRollbackOnly();
        });

        assertThat(deliveryCount()).isZero();
    }

    @Test
    void deliveryFailureDoesNotEscapeOrRollBackPublisher() {
        eventPublisher.publishEvent(orderConfirmed());

        assertThatCode(() -> new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            jdbcTemplate.update("UPDATE delivery SET category = 'TABLE' WHERE order_id = ?", ORDER_ID);
            eventPublisher.publishEvent(new OrderCancellationRequested(ORDER_ID, " "));
        })).doesNotThrowAnyException();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT category FROM delivery WHERE order_id = ?", String.class, ORDER_ID
        )).isEqualTo("TABLE");
        assertThat(deliveryStatus()).isEqualTo("REQUESTED");
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
                "010-0000-0002"
        );
    }
}
