package setty.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static setty.global.event.EventPublicationTestSupport.awaitEventsHandled;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import setty.common.DeliveryCancellationRejected;
import setty.common.DeliveryCancelled;
import setty.common.OrderCancellationRequested;
import setty.common.OrderConfirmed;
import setty.delivery.domain.DeliveryId;
import setty.delivery.domain.DriverId;

@SpringBootTest
@Testcontainers
class DeliveryCancellationIntegrationTest {

    private static final long ORDER_ID = 101L;
    private static final String CANCELLATION_REQUEST_ID = "cancel-request-1";
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
    private DeliveryLifecycleService deliveryLifecycleService;

    @Autowired
    private DeliveryReplyRecorder replies;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM EVENT_PUBLICATION");
        jdbcTemplate.update("DELETE FROM delivery");
        jdbcTemplate.update("DELETE FROM delivery_order_decision");
        replies.clear();
    }

    @Test
    void requestedDeliveryIsCancelled() {
        final DeliveryId deliveryId = prepareRequestedDelivery();

        publishCommitted(cancellationRequested());

        assertThat(deliveryStatus()).isEqualTo("CANCELLED");
        final DeliveryCancelled event = singleCancelled();
        assertThat(event.deliveryId()).isEqualTo(deliveryId.value());
        assertThat(event.orderId()).isEqualTo(ORDER_ID);
        assertThat(event.cancellationRequestId()).isEqualTo(CANCELLATION_REQUEST_ID);
        assertThat(event.decidedAt()).isNotNull();
        assertThat(replies.rejected).isEmpty();
    }

    @Test
    void acceptedDeliveryRejectsCancellationAndKeepsStatus() {
        final DeliveryId deliveryId = prepareRequestedDelivery();
        deliveryLifecycleService.accept(deliveryId, DRIVER_ID, Instant.now());
        awaitEventsHandled(jdbcTemplate);

        publishCommitted(cancellationRequested());

        assertThat(deliveryStatus()).isEqualTo("ACCEPTED");
        final List<DeliveryCancellationRejected> events = replies.rejected;
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().deliveryId()).isEqualTo(deliveryId.value());
        assertThat(events.getFirst().orderId()).isEqualTo(ORDER_ID);
        assertThat(events.getFirst().cancellationRequestId()).isEqualTo(CANCELLATION_REQUEST_ID);
        assertThat(replies.cancelled).isEmpty();
    }

    @Test
    void cancellationBeforeDeliveryRequestBlocksLateRequest() {
        publishCommitted(cancellationRequested());

        final DeliveryCancelled event = singleCancelled();
        assertThat(event.deliveryId()).isNull();
        assertThat(event.orderId()).isEqualTo(ORDER_ID);
        assertThat(decisionOutcome()).isEqualTo("CANCELLED");

        // 재발행 등으로 뒤늦게 도착한 배송 요청은 이미 취소로 판정된 주문이므로 만들지 않는다.
        publishCommitted(orderConfirmed());

        assertThat(deliveryCount()).isZero();
        assertThat(decisionOutcome()).isEqualTo("CANCELLED");
    }

    @Test
    void repeatedCancellationRequestRespondsCancelledAgain() {
        final DeliveryId deliveryId = prepareRequestedDelivery();

        publishCommitted(cancellationRequested());
        publishCommitted(cancellationRequested());

        final List<DeliveryCancelled> events = replies.cancelled;
        assertThat(events).hasSize(2);
        assertThat(events).allSatisfy(event -> {
            assertThat(event.deliveryId()).isEqualTo(deliveryId.value());
            assertThat(event.cancellationRequestId()).isEqualTo(CANCELLATION_REQUEST_ID);
        });
        assertThat(deliveryStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void repeatedCancellationBeforeDeliveryRequestRespondsCancelledAgain() {
        publishCommitted(cancellationRequested());
        publishCommitted(cancellationRequested());

        assertThat(replies.cancelled)
                .hasSize(2)
                .allSatisfy(event -> assertThat(event.deliveryId()).isNull());
        assertThat(decisionOutcome()).isEqualTo("CANCELLED");
        assertThat(deliveryCount()).isZero();
    }

    private DeliveryId prepareRequestedDelivery() {
        publishCommitted(orderConfirmed());
        return new DeliveryId(jdbcTemplate.queryForObject(
                "SELECT id FROM delivery WHERE order_id = ?",
                Long.class,
                ORDER_ID
        ));
    }

    // 모듈 간 이벤트는 발행한 쪽이 커밋된 뒤 비동기로 전달되므로 처리가 끝날 때까지 기다린다.
    private void publishCommitted(final Object event) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> eventPublisher.publishEvent(event));
        awaitEventsHandled(jdbcTemplate);
    }

    private DeliveryCancelled singleCancelled() {
        assertThat(replies.cancelled).hasSize(1);
        return replies.cancelled.getFirst();
    }

    private String deliveryStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM delivery WHERE order_id = ?",
                String.class,
                ORDER_ID
        );
    }

    private String decisionOutcome() {
        return jdbcTemplate.queryForObject(
                "SELECT outcome FROM delivery_order_decision WHERE order_id = ?",
                String.class,
                ORDER_ID
        );
    }

    private long deliveryCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM delivery", Long.class);
    }

    private static OrderCancellationRequested cancellationRequested() {
        return new OrderCancellationRequested(ORDER_ID, CANCELLATION_REQUEST_ID);
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

    // 배송의 취소 응답은 리스너 스레드에서 발행되므로 스레드와 무관하게 모아 둔다.
    @TestConfiguration
    static class DeliveryReplyRecorder {

        private final List<DeliveryCancelled> cancelled = new CopyOnWriteArrayList<>();
        private final List<DeliveryCancellationRejected> rejected = new CopyOnWriteArrayList<>();

        @EventListener
        void on(final DeliveryCancelled event) {
            cancelled.add(event);
        }

        @EventListener
        void on(final DeliveryCancellationRejected event) {
            rejected.add(event);
        }

        void clear() {
            cancelled.clear();
            rejected.clear();
        }
    }
}
