package setty.delivery.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import setty.global.exception.BusinessException;

class DeliveryValueObjectTest {

    @Test
    void identifiersMustBePositive() {
        assertInvalid(() -> new DeliveryId(0L));
        assertInvalid(() -> new OrderId(-1L));
        assertInvalid(() -> new DriverId(null));
    }

    @Test
    void textValuesRejectNullAndBlankAndTrimValidValues() {
        assertInvalid(() -> new Address(" "));
        assertInvalid(() -> new PhoneNumber(null));
        assertInvalid(() -> new FurnitureInfo("의자", " "));

        assertThat(new Address("  가상 주소  ").value()).isEqualTo("가상 주소");
        assertThat(new PhoneNumber("  010-0000-0000  ").value()).isEqualTo("010-0000-0000");
    }

    @Test
    void estimatedFeeCannotBeNegative() {
        assertInvalid(() -> new EstimatedDeliveryFee(-1));
        assertThat(new EstimatedDeliveryFee(0).value()).isZero();
    }

    @Test
    void routeRequiresAddressesAndPhoneNumbers() {
        assertInvalid(() -> new DeliveryRoute(
                null,
                new Address("가상 도착지"),
                new PhoneNumber("010-0000-0001"),
                new PhoneNumber("010-0000-0002")
        ));
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
