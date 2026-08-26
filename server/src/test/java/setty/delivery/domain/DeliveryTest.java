package setty.delivery.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import setty.common.DeliveryStatus;
import setty.global.exception.BusinessException;

class DeliveryTest {

    private static final DriverId DRIVER_ID = new DriverId(10L);
    private static final DriverId OTHER_DRIVER_ID = new DriverId(20L);
    private static final Instant REQUESTED_AT = Instant.parse("2026-08-26T01:00:00Z");
    private static final Instant ACCEPTED_AT = Instant.parse("2026-08-26T01:10:00Z");
    private static final Instant PICKED_UP_AT = Instant.parse("2026-08-26T02:00:00Z");
    private static final Instant DELIVERED_AT = Instant.parse("2026-08-26T03:00:00Z");

    @Test
    void requestedDeliveryStartsWithRequestedStatus() {
        final Delivery delivery = requestDelivery();

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.REQUESTED);
        assertThat(delivery.getRequestedAt()).isEqualTo(REQUESTED_AT);
        assertThat(delivery.getDriverId()).isNull();
    }

    @Test
    void requestedDeliveryCanBeAccepted() {
        final Delivery delivery = requestDelivery();

        delivery.accept(DRIVER_ID, ACCEPTED_AT);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.ACCEPTED);
    }

    @Test
    void acceptanceRecordsDriverAndAcceptedTime() {
        final Delivery delivery = requestDelivery();

        delivery.accept(DRIVER_ID, ACCEPTED_AT);

        assertThat(delivery.getDriverId()).isEqualTo(DRIVER_ID);
        assertThat(delivery.getAcceptedAt()).isEqualTo(ACCEPTED_AT);
    }

    @Test
    void acceptedDeliveryCannotBeAcceptedAgain() {
        final Delivery delivery = acceptedDelivery();

        assertThatThrownBy(() -> delivery.accept(OTHER_DRIVER_ID, ACCEPTED_AT))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void acceptedDeliveryCanBePickedUp() {
        final Delivery delivery = acceptedDelivery();

        delivery.pickUp(DRIVER_ID, PICKED_UP_AT);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PICKED_UP);
        assertThat(delivery.getPickedUpAt()).isEqualTo(PICKED_UP_AT);
    }

    @Test
    void differentDriverCannotPickUpDelivery() {
        final Delivery delivery = acceptedDelivery();

        assertThatThrownBy(() -> delivery.pickUp(OTHER_DRIVER_ID, PICKED_UP_AT))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void pickedUpDeliveryCanBeCompleted() {
        final Delivery delivery = pickedUpDelivery();

        delivery.complete(DRIVER_ID, DELIVERED_AT);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(delivery.getDeliveredAt()).isEqualTo(DELIVERED_AT);
    }

    @Test
    void differentDriverCannotCompleteDelivery() {
        final Delivery delivery = pickedUpDelivery();

        assertThatThrownBy(() -> delivery.complete(OTHER_DRIVER_ID, DELIVERED_AT))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void invalidStatusTransitionIsRejected() {
        final Delivery delivery = requestDelivery();

        assertThatThrownBy(() -> delivery.pickUp(DRIVER_ID, PICKED_UP_AT))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> delivery.complete(DRIVER_ID, DELIVERED_AT))
                .isInstanceOf(BusinessException.class);
    }

    private static Delivery pickedUpDelivery() {
        final Delivery delivery = acceptedDelivery();
        delivery.pickUp(DRIVER_ID, PICKED_UP_AT);
        return delivery;
    }

    private static Delivery acceptedDelivery() {
        final Delivery delivery = requestDelivery();
        delivery.accept(DRIVER_ID, ACCEPTED_AT);
        return delivery;
    }

    private static Delivery requestDelivery() {
        return Delivery.request(
                new OrderId(1L),
                new FurnitureInfo("가상 원목 의자", "CHAIR"),
                new DeliveryRoute(
                        new Address("서울시 가상구 출발로 1"),
                        new Address("서울시 가상구 도착로 2"),
                        new PhoneNumber("010-0000-0001"),
                        new PhoneNumber("010-0000-0002")
                ),
                new EstimatedDeliveryFee(10_000),
                REQUESTED_AT
        );
    }
}
