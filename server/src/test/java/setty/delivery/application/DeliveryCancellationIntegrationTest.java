package setty.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import setty.common.DeliveryCancellationRejected;
import setty.common.DeliveryCancelled;
import setty.common.OrderCancellationRequested;
import setty.common.OrderRequested;
import setty.delivery.domain.DeliveryId;
import setty.delivery.domain.DriverId;

@SpringBootTest
@Testcontainers
@RecordApplicationEvents
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
    private ApplicationEvents applicationEvents;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM delivery_cancellation");
        jdbcTemplate.update("DELETE FROM delivery");
    }

    @Test
    void requestedDeliveryIsCancelledAndKeptAsRecord() {
        final DeliveryId deliveryId = prepareRequestedDelivery();

        eventPublisher.publishEvent(cancellationRequested(CANCELLATION_REQUEST_ID));

        assertThat(deliveryStatus()).isEqualTo("CANCELLED");
        final DeliveryCancelled event = singleCancelled();
        assertThat(event.deliveryId()).isEqualTo(deliveryId.value());
        assertThat(event.orderId()).isEqualTo(ORDER_ID);
        assertThat(event.cancellationRequestId()).isEqualTo(CANCELLATION_REQUEST_ID);
        assertThat(event.decidedAt()).isNotNull();
        final Map<String, Object> cancellation = jdbcTemplate.queryForMap("SELECT * FROM delivery_cancellation");
        assertThat(cancellation.get("order_id")).isEqualTo(ORDER_ID);
        assertThat(cancellation.get("delivery_id")).isEqualTo(deliveryId.value());
        assertThat(applicationEvents.stream(DeliveryCancellationRejected.class)).isEmpty();
    }

    @Test
    void acceptedDeliveryRejectsCancellationAndKeepsStatus() {
        final DeliveryId deliveryId = prepareRequestedDelivery();
        deliveryLifecycleService.accept(deliveryId, DRIVER_ID, Instant.now());

        eventPublisher.publishEvent(cancellationRequested(CANCELLATION_REQUEST_ID));

        assertThat(deliveryStatus()).isEqualTo("ACCEPTED");
        final List<DeliveryCancellationRejected> events =
                applicationEvents.stream(DeliveryCancellationRejected.class).toList();
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().deliveryId()).isEqualTo(deliveryId.value());
        assertThat(events.getFirst().orderId()).isEqualTo(ORDER_ID);
        assertThat(events.getFirst().cancellationRequestId()).isEqualTo(CANCELLATION_REQUEST_ID);
        assertThat(applicationEvents.stream(DeliveryCancelled.class)).isEmpty();
        assertThat(cancellationCount()).isZero();
    }

    @Test
    void cancellationBeforeDeliveryRequestLeavesRecordAndBlocksLateRequest() {
        eventPublisher.publishEvent(cancellationRequested(CANCELLATION_REQUEST_ID));

        final DeliveryCancelled event = singleCancelled();
        assertThat(event.deliveryId()).isNull();
        assertThat(event.orderId()).isEqualTo(ORDER_ID);
        assertThat(cancellationCount()).isEqualTo(1L);

        eventPublisher.publishEvent(orderRequested());

        assertThat(deliveryCount()).isZero();
    }

    @Test
    void repeatedCancellationRequestRespondsCancelledAgain() {
        final DeliveryId deliveryId = prepareRequestedDelivery();

        eventPublisher.publishEvent(cancellationRequested(CANCELLATION_REQUEST_ID));
        eventPublisher.publishEvent(cancellationRequested(CANCELLATION_REQUEST_ID));

        final List<DeliveryCancelled> events = applicationEvents.stream(DeliveryCancelled.class).toList();
        assertThat(events).hasSize(2);
        assertThat(events).allSatisfy(event -> {
            assertThat(event.deliveryId()).isEqualTo(deliveryId.value());
            assertThat(event.cancellationRequestId()).isEqualTo(CANCELLATION_REQUEST_ID);
        });
        assertThat(cancellationCount()).isEqualTo(1L);
        assertThat(deliveryStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void repeatedCancellationBeforeDeliveryRequestKeepsOneRecord() {
        eventPublisher.publishEvent(cancellationRequested(CANCELLATION_REQUEST_ID));
        eventPublisher.publishEvent(cancellationRequested(CANCELLATION_REQUEST_ID));

        assertThat(applicationEvents.stream(DeliveryCancelled.class))
                .hasSize(2)
                .allSatisfy(event -> assertThat(event.deliveryId()).isNull());
        assertThat(cancellationCount()).isEqualTo(1L);
    }

    private DeliveryId prepareRequestedDelivery() {
        eventPublisher.publishEvent(orderRequested());
        return new DeliveryId(jdbcTemplate.queryForObject(
                "SELECT id FROM delivery WHERE order_id = ?",
                Long.class,
                ORDER_ID
        ));
    }

    private DeliveryCancelled singleCancelled() {
        final List<DeliveryCancelled> events = applicationEvents.stream(DeliveryCancelled.class).toList();
        assertThat(events).hasSize(1);
        return events.getFirst();
    }

    private String deliveryStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM delivery WHERE order_id = ?",
                String.class,
                ORDER_ID
        );
    }

    private long deliveryCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM delivery", Long.class);
    }

    private long cancellationCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM delivery_cancellation", Long.class);
    }

    private static OrderCancellationRequested cancellationRequested(final String cancellationRequestId) {
        return new OrderCancellationRequested(ORDER_ID, cancellationRequestId);
    }

    private static OrderRequested orderRequested() {
        return new OrderRequested(
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
