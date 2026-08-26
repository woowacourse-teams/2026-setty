package setty.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
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
import setty.common.DeliveryStatus;
import setty.common.DeliveryStatusChanged;
import setty.common.OrderRequested;
import setty.delivery.domain.DeliveryId;
import setty.delivery.domain.DriverId;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.platform.order.repository.OrderRepository;

@SpringBootTest
@Testcontainers
@RecordApplicationEvents
class DeliveryStatusChangeIntegrationTest {

    private static final long ORDER_ID = 101L;
    private static final DriverId DRIVER_ID = new DriverId(201L);
    private static final DriverId OTHER_DRIVER_ID = new DriverId(202L);
    private static final Instant REQUESTED_AT = Instant.parse("2026-08-26T01:00:00Z");
    private static final Instant ACCEPTED_AT = Instant.parse("2026-08-26T01:10:00Z");
    private static final Instant PICKED_UP_AT = Instant.parse("2026-08-26T02:00:00Z");
    private static final Instant DELIVERED_AT = Instant.parse("2026-08-26T03:00:00Z");

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.11")
            .withDatabaseName("setty_test")
            .withUsername("setty_test")
            .withPassword("setty_test");

    @Autowired
    private AcceptDeliveryService acceptDeliveryService;

    @Autowired
    private PickupDeliveryService pickupDeliveryService;

    @Autowired
    private CompleteDeliveryService completeDeliveryService;

    @Autowired
    private RegisterDeliveryService registerDeliveryService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private ApplicationEvents applicationEvents;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM delivery");
        jdbcTemplate.update("DELETE FROM orders");
    }

    @Test
    void acceptancePublishesEventAndCommitsBothStatuses() {
        final DeliveryId deliveryId = prepareRequestedDeliveryAndOrder();

        acceptDeliveryService.accept(deliveryId, DRIVER_ID, ACCEPTED_AT);

        assertEvent("ACCEPTED", ACCEPTED_AT);
        assertStatuses(deliveryId, "ACCEPTED");
    }

    @Test
    void pickupPublishesEventAndCommitsBothStatuses() {
        final DeliveryId deliveryId = prepareAcceptedDeliveryAndOrder();

        pickupDeliveryService.pickUp(deliveryId, DRIVER_ID, PICKED_UP_AT);

        assertEvent("PICKED_UP", PICKED_UP_AT);
        assertStatuses(deliveryId, "PICKED_UP");
    }

    @Test
    void completionPublishesEventAndCommitsBothStatuses() {
        final DeliveryId deliveryId = preparePickedUpDeliveryAndOrder();

        completeDeliveryService.complete(deliveryId, DRIVER_ID, DELIVERED_AT);

        assertEvent("DELIVERED", DELIVERED_AT);
        assertStatuses(deliveryId, "DELIVERED");
    }

    @Test
    void duplicatedStatusEventIsIdempotent() {
        final DeliveryId deliveryId = prepareRequestedDeliveryAndOrder();
        final DeliveryStatusChanged event = new DeliveryStatusChanged(
                deliveryId.value(), ORDER_ID, "ACCEPTED", ACCEPTED_AT
        );

        eventPublisher.publishEvent(event);
        eventPublisher.publishEvent(event);

        assertThat(orderStatus()).isEqualTo("ACCEPTED");
    }

    @Test
    void mismatchedOrderStatusIsRejected() {
        final DeliveryId deliveryId = prepareRequestedDeliveryAndOrder();
        updateOrderStatus("DELIVERED");

        assertBusinessError(
                () -> eventPublisher.publishEvent(new DeliveryStatusChanged(
                        deliveryId.value(), ORDER_ID, "PICKED_UP", PICKED_UP_AT
                )),
                ErrorCode.ORDER_DELIVERY_STATUS_MISMATCH
        );
        assertThat(orderStatus()).isEqualTo("DELIVERED");
    }

    @Test
    void orderUpdateFailureRollsBackDeliveryChange() {
        final DeliveryId deliveryId = prepareRequestedDeliveryAndOrder();
        updateOrderStatus("DELIVERED");

        assertBusinessError(
                () -> acceptDeliveryService.accept(deliveryId, DRIVER_ID, ACCEPTED_AT),
                ErrorCode.ORDER_DELIVERY_STATUS_MISMATCH
        );

        assertThat(deliveryStatus(deliveryId)).isEqualTo("REQUESTED");
        assertThat(orderStatus()).isEqualTo("DELIVERED");
        assertThat(deliveryDriverId(deliveryId)).isNull();
    }

    @Test
    void deliveryChangeFailureDoesNotUpdateOrder() {
        final DeliveryId deliveryId = prepareRequestedDeliveryAndOrder();

        assertBusinessError(
                () -> pickupDeliveryService.pickUp(deliveryId, DRIVER_ID, PICKED_UP_AT),
                ErrorCode.INVALID_STATUS_TRANSITION
        );

        assertStatuses(deliveryId, "REQUESTED");
    }

    @Test
    void differentDriverCannotPickUpOrCompleteDelivery() {
        final DeliveryId deliveryId = prepareAcceptedDeliveryAndOrder();

        assertBusinessError(
                () -> pickupDeliveryService.pickUp(deliveryId, OTHER_DRIVER_ID, PICKED_UP_AT),
                ErrorCode.INVALID_STATUS_TRANSITION
        );
        assertStatuses(deliveryId, "ACCEPTED");

        pickupDeliveryService.pickUp(deliveryId, DRIVER_ID, PICKED_UP_AT);

        assertBusinessError(
                () -> completeDeliveryService.complete(deliveryId, OTHER_DRIVER_ID, DELIVERED_AT),
                ErrorCode.INVALID_STATUS_TRANSITION
        );
        assertStatuses(deliveryId, "PICKED_UP");
    }

    private DeliveryId prepareRequestedDeliveryAndOrder() {
        insertOrder("REQUESTED");
        registerDeliveryService.register(orderRequested(), REQUESTED_AT);
        return new DeliveryId(jdbcTemplate.queryForObject(
                "SELECT id FROM delivery WHERE order_id = ?",
                Long.class,
                ORDER_ID
        ));
    }

    private DeliveryId prepareAcceptedDeliveryAndOrder() {
        final DeliveryId deliveryId = prepareRequestedDeliveryAndOrder();
        acceptDeliveryService.accept(deliveryId, DRIVER_ID, ACCEPTED_AT);
        return deliveryId;
    }

    private DeliveryId preparePickedUpDeliveryAndOrder() {
        final DeliveryId deliveryId = prepareAcceptedDeliveryAndOrder();
        pickupDeliveryService.pickUp(deliveryId, DRIVER_ID, PICKED_UP_AT);
        return deliveryId;
    }

    private void insertOrder(final String status) {
        jdbcTemplate.update(
                "INSERT INTO orders (id, listing_id, buyer_id, delivery_status) VALUES (?, ?, ?, ?)",
                ORDER_ID,
                301L,
                401L,
                status
        );
    }

    private void updateOrderStatus(final String status) {
        jdbcTemplate.update("UPDATE orders SET delivery_status = ? WHERE id = ?", status, ORDER_ID);
    }

    private void assertEvent(final String expectedStatus, final Instant expectedChangedAt) {
        final List<DeliveryStatusChanged> events = applicationEvents.stream(DeliveryStatusChanged.class).toList();
        final DeliveryStatusChanged event = events.getLast();
        assertThat(event.deliveryId()).isPositive();
        assertThat(event.orderId()).isEqualTo(ORDER_ID);
        assertThat(event.status()).isEqualTo(expectedStatus);
        assertThat(event.changedAt()).isEqualTo(expectedChangedAt);
    }

    private void assertStatuses(final DeliveryId deliveryId, final String expectedStatus) {
        assertThat(deliveryStatus(deliveryId)).isEqualTo(expectedStatus);
        assertThat(orderStatus()).isEqualTo(expectedStatus);
    }

    private String deliveryStatus(final DeliveryId deliveryId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM delivery WHERE id = ?",
                String.class,
                deliveryId.value()
        );
    }

    private Long deliveryDriverId(final DeliveryId deliveryId) {
        return jdbcTemplate.queryForObject(
                "SELECT driver_id FROM delivery WHERE id = ?",
                Long.class,
                deliveryId.value()
        );
    }

    private String orderStatus() {
        final DeliveryStatus deliveryStatus = orderRepository.findById(ORDER_ID)
                .orElseThrow()
                .getDeliveryStatus();
        return deliveryStatus.name();
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

    private static void assertBusinessError(final Runnable action, final ErrorCode expectedErrorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(expectedErrorCode)
                );
    }
}
