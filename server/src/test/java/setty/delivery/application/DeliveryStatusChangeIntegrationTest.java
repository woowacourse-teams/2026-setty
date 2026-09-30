package setty.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import setty.common.DeliveryAccepted;
import setty.common.DeliveryDelivered;
import setty.common.DeliveryPickedUp;
import setty.delivery.domain.DeliveryId;
import setty.delivery.domain.DriverId;
import setty.delivery.domain.OrderId;
import setty.delivery.domain.delivery.DeliveryPoint;
import setty.delivery.domain.delivery.DeliveryRoute;
import setty.delivery.domain.delivery.EstimatedDeliveryFee;
import setty.delivery.domain.delivery.FurnitureInfo;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

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
    private DeliveryLifecycleService deliveryLifecycleService;

    @Autowired
    private RegisterDeliveryService registerDeliveryService;

    @Autowired
    private ApplicationEvents applicationEvents;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM delivery");
    }

    @Test
    void acceptancePublishesDeliveryAcceptedAndCommitsStatus() {
        final DeliveryId deliveryId = prepareRequestedDelivery();

        deliveryLifecycleService.accept(deliveryId, DRIVER_ID, ACCEPTED_AT);

        assertThat(applicationEvents.stream(DeliveryAccepted.class))
                .containsExactly(new DeliveryAccepted(deliveryId.value(), ORDER_ID, ACCEPTED_AT));
        assertThat(deliveryStatus(deliveryId)).isEqualTo("ACCEPTED");
    }

    @Test
    void pickupPublishesDeliveryPickedUpAndCommitsStatus() {
        final DeliveryId deliveryId = prepareAcceptedDelivery();

        deliveryLifecycleService.pickUp(deliveryId, DRIVER_ID, PICKED_UP_AT);

        assertThat(applicationEvents.stream(DeliveryPickedUp.class))
                .containsExactly(new DeliveryPickedUp(deliveryId.value(), ORDER_ID, PICKED_UP_AT));
        assertThat(deliveryStatus(deliveryId)).isEqualTo("PICKED_UP");
    }

    @Test
    void completionPublishesDeliveryDeliveredAndCommitsStatus() {
        final DeliveryId deliveryId = prepareAcceptedDelivery();
        deliveryLifecycleService.pickUp(deliveryId, DRIVER_ID, PICKED_UP_AT);

        deliveryLifecycleService.complete(deliveryId, DRIVER_ID, DELIVERED_AT);

        assertThat(applicationEvents.stream(DeliveryDelivered.class))
                .containsExactly(new DeliveryDelivered(deliveryId.value(), ORDER_ID, DELIVERED_AT));
        assertThat(deliveryStatus(deliveryId)).isEqualTo("DELIVERED");
    }

    @Test
    void failedTransitionPublishesNothing() {
        final DeliveryId deliveryId = prepareRequestedDelivery();

        assertBusinessError(
                () -> deliveryLifecycleService.pickUp(deliveryId, DRIVER_ID, PICKED_UP_AT),
                ErrorCode.INVALID_DELIVERY_TRANSITION
        );

        assertThat(applicationEvents.stream(DeliveryPickedUp.class)).isEmpty();
        assertThat(deliveryStatus(deliveryId)).isEqualTo("REQUESTED");
    }

    @Test
    void differentDriverCannotPickUpOrCompleteDelivery() {
        final DeliveryId deliveryId = prepareAcceptedDelivery();

        assertBusinessError(
                () -> deliveryLifecycleService.pickUp(deliveryId, OTHER_DRIVER_ID, PICKED_UP_AT),
                ErrorCode.DELIVERY_DRIVER_MISMATCH
        );
        assertThat(deliveryStatus(deliveryId)).isEqualTo("ACCEPTED");

        deliveryLifecycleService.pickUp(deliveryId, DRIVER_ID, PICKED_UP_AT);

        assertBusinessError(
                () -> deliveryLifecycleService.complete(deliveryId, OTHER_DRIVER_ID, DELIVERED_AT),
                ErrorCode.DELIVERY_DRIVER_MISMATCH
        );
        assertThat(deliveryStatus(deliveryId)).isEqualTo("PICKED_UP");
    }

    private DeliveryId prepareRequestedDelivery() {
        registerDeliveryService.register(
                OrderId.from(ORDER_ID),
                FurnitureInfo.of("가상 원목 의자", "CHAIR"),
                DeliveryRoute.of(
                        DeliveryPoint.pickup("서울시 가상구 출발로 1", "010-0000-0001"),
                        DeliveryPoint.destination("서울시 가상구 도착로 2", "010-0000-0002")
                ),
                EstimatedDeliveryFee.from(10_000),
                REQUESTED_AT
        );
        return new DeliveryId(jdbcTemplate.queryForObject(
                "SELECT id FROM delivery WHERE order_id = ?",
                Long.class,
                ORDER_ID
        ));
    }

    private DeliveryId prepareAcceptedDelivery() {
        final DeliveryId deliveryId = prepareRequestedDelivery();
        deliveryLifecycleService.accept(deliveryId, DRIVER_ID, ACCEPTED_AT);
        return deliveryId;
    }

    private String deliveryStatus(final DeliveryId deliveryId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM delivery WHERE id = ?",
                String.class,
                deliveryId.value()
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
