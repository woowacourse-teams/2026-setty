package setty.delivery.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import setty.delivery.domain.delivery.Address;
import setty.delivery.domain.delivery.DeliveryAssignment;
import setty.delivery.domain.delivery.DeliveryPoint;
import setty.delivery.domain.delivery.DeliveryRoute;
import setty.delivery.domain.delivery.EstimatedDeliveryFee;
import setty.delivery.domain.delivery.FurnitureInfo;
import setty.delivery.domain.delivery.PhoneNumber;
import setty.global.exception.BusinessException;

class DeliveryValueObjectTest {

    @Test
    void identifiersMustBePositive() {
        assertInvalid(() -> new DeliveryId(0L));
        assertInvalid(() -> OrderId.from(-1L));
        assertInvalid(() -> new DriverId(null));
    }

    @Test
    void textValuesRejectNullAndBlankAndTrimValidValues() {
        assertInvalid(() -> new Address(" "));
        assertInvalid(() -> new PhoneNumber(null));
        assertInvalid(() -> FurnitureInfo.of("의자", " "));

        assertThat(new Address("  가상 주소  ").value()).isEqualTo("가상 주소");
        assertThat(new PhoneNumber("  010-0000-0000  ").value()).isEqualTo("010-0000-0000");
    }

    @Test
    void estimatedFeeCannotBeNegative() {
        assertInvalid(() -> EstimatedDeliveryFee.from(-1));
        assertThat(EstimatedDeliveryFee.from(0).value()).isZero();
    }

    @Test
    void routeRequiresPickupAndDestination() {
        final DeliveryPoint point = DeliveryPoint.destination("가상 도착지", "010-0000-0002");

        assertInvalid(() -> DeliveryRoute.of(null, point));
        assertInvalid(() -> DeliveryRoute.of(point, null));
    }

    @Test
    void pointRequiresAddressAndPhoneNumber() {
        assertInvalid(() -> DeliveryPoint.pickup(null, "010-0000-0001"));
        assertInvalid(() -> DeliveryPoint.pickup("가상 출발지", " "));
    }

    @Test
    void assignmentRequiresDriverAndAcceptedTime() {
        assertInvalid(() -> new DeliveryAssignment(null, Instant.now()));
        assertInvalid(() -> new DeliveryAssignment(new DriverId(1L), null));
    }

    private static void assertInvalid(final Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(BusinessException.class);
    }
}
