package setty.delivery.domain;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import java.util.Objects;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

@Embeddable
public class DeliveryRoute {

    @Embedded
    @AttributeOverride(
            name = "value",
            column = @Column(name = "pickup_address", nullable = false, length = 255)
    )
    private Address pickupAddress;

    @Embedded
    @AttributeOverride(
            name = "value",
            column = @Column(name = "delivery_address", nullable = false, length = 255)
    )
    private Address deliveryAddress;

    @Embedded
    @AttributeOverride(
            name = "value",
            column = @Column(name = "pickup_phone_number", nullable = false, length = 30)
    )
    private PhoneNumber pickupPhoneNumber;

    @Embedded
    @AttributeOverride(
            name = "value",
            column = @Column(name = "delivery_phone_number", nullable = false, length = 30)
    )
    private PhoneNumber deliveryPhoneNumber;

    protected DeliveryRoute() {
    }

    public DeliveryRoute(
            final Address pickupAddress,
            final Address deliveryAddress,
            final PhoneNumber pickupPhoneNumber,
            final PhoneNumber deliveryPhoneNumber
    ) {
        if (pickupAddress == null
                || deliveryAddress == null
                || pickupPhoneNumber == null
                || deliveryPhoneNumber == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        this.pickupAddress = pickupAddress;
        this.deliveryAddress = deliveryAddress;
        this.pickupPhoneNumber = pickupPhoneNumber;
        this.deliveryPhoneNumber = deliveryPhoneNumber;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof DeliveryRoute that)) {
            return false;
        }
        return Objects.equals(pickupAddress, that.pickupAddress)
                && Objects.equals(deliveryAddress, that.deliveryAddress)
                && Objects.equals(pickupPhoneNumber, that.pickupPhoneNumber)
                && Objects.equals(deliveryPhoneNumber, that.deliveryPhoneNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pickupAddress, deliveryAddress, pickupPhoneNumber, deliveryPhoneNumber);
    }
}
