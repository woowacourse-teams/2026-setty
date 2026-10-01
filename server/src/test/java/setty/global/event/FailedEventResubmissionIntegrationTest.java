package setty.global.event;

import static org.assertj.core.api.Assertions.assertThat;
import static setty.global.event.EventPublicationTestSupport.awaitEventsHandled;
import static setty.global.event.EventPublicationTestSupport.incompletePublicationCount;

import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
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
import setty.common.DeliveryAccepted;
import setty.common.OrderConfirmed;
import setty.delivery.application.DeliveryLifecycleService;
import setty.delivery.domain.DeliveryId;
import setty.delivery.domain.DriverId;

@SpringBootTest
@Testcontainers
class FailedEventResubmissionIntegrationTest {

    private static final long SELLER_ID = 1L;
    private static final long BUYER_ID = 2L;
    private static final long LISTING_ID = 10L;
    private static final long ORDER_ID = 100L;
    private static final DriverId DRIVER_ID = new DriverId(201L);

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.11")
            .withDatabaseName("setty_test")
            .withUsername("setty_test")
            .withPassword("setty_test");

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DeliveryLifecycleService deliveryLifecycleService;

    @Autowired
    private FailedEventResubmissionScheduler resubmissionScheduler;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        awaitEventsHandled(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM EVENT_PUBLICATION");
        jdbcTemplate.update("DELETE FROM delivery");
        jdbcTemplate.update("DELETE FROM delivery_order_decision");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM listings");
        jdbcTemplate.update("DELETE FROM members");
    }

    @Test
    void 배송_수락_뒤_주문_처리가_실패해도_수락은_유지되고_재발행으로_반영된다() {
        final DeliveryId deliveryId = prepareRequestedDelivery();

        deliveryLifecycleService.accept(deliveryId, DRIVER_ID, Instant.now());
        awaitEventsHandled(jdbcTemplate);

        // 주문이 아직 없어 주문·매물 쪽 처리는 실패하지만 기사 수락은 롤백되지 않는다.
        assertThat(deliveryStatus()).isEqualTo("ACCEPTED");
        assertThat(incompletePublicationCount(jdbcTemplate, DeliveryAccepted.class)).isEqualTo(2);

        insertConfirmedOrder();
        resubmissionScheduler.resubmitFailedPublications();
        awaitEventsHandled(jdbcTemplate);

        assertThat(orderDeliveryStatus()).isEqualTo("ACCEPTED");
        assertThat(listingSaleStatus()).isEqualTo("RESERVED");
        assertThat(incompletePublicationCount(jdbcTemplate, DeliveryAccepted.class)).isZero();
    }

    private DeliveryId prepareRequestedDelivery() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> eventPublisher.publishEvent(
                new OrderConfirmed(
                        ORDER_ID,
                        "가상 원목 의자",
                        "CHAIR",
                        "서울시 가상구 출발로 1",
                        "서울시 가상구 도착로 2",
                        10_000,
                        "010-0000-0001",
                        "010-0000-0002"
                )
        ));
        awaitEventsHandled(jdbcTemplate);
        return new DeliveryId(jdbcTemplate.queryForObject(
                "SELECT id FROM delivery WHERE order_id = ?", Long.class, ORDER_ID
        ));
    }

    private void insertConfirmedOrder() {
        insertMember(SELLER_ID);
        insertMember(BUYER_ID);
        jdbcTemplate.update(
                """
                INSERT INTO listings (id, seller_id, title, description, price, delivery_fee, category,
                                      condition_grade, width_cm, depth_cm, height_cm, sale_status,
                                      has_purchase_request, created_at, updated_at, deleted_at)
                VALUES (?, ?, '가상 원목 의자', '가상 설명', 50000, 10000, 'CHAIR',
                        'A', 40, 40, 80, 'AVAILABLE', true, NOW(6), NOW(6), NULL)
                """,
                LISTING_ID,
                SELLER_ID
        );
        jdbcTemplate.update(
                """
                INSERT INTO orders (id, listing_id, buyer_id, delivery_status, order_status)
                VALUES (?, ?, ?, 'REQUESTED', 'CONFIRMED')
                """,
                ORDER_ID,
                LISTING_ID,
                BUYER_ID
        );
    }

    private void insertMember(final long memberId) {
        jdbcTemplate.update(
                """
                INSERT INTO members (id, login_id, password, role, phone_number, address, token)
                VALUES (?, ?, 'encoded-password', 'MEMBER', '010-0000-0000', '가상 주소', ?)
                """,
                memberId,
                "member" + memberId,
                "token-" + memberId
        );
    }

    private String deliveryStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM delivery WHERE order_id = ?", String.class, ORDER_ID
        );
    }

    private String orderDeliveryStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT delivery_status FROM orders WHERE id = ?", String.class, ORDER_ID
        );
    }

    private String listingSaleStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT sale_status FROM listings WHERE id = ?", String.class, LISTING_ID
        );
    }
}
