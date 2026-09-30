package setty.delivery.domain.delivery;

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
            name = "address.value",
            column = @Column(name = "pickup_address", nullable = false, length = 255)
    )
    @AttributeOverride(
            name = "phoneNumber.value",
            column = @Column(name = "pickup_phone_number", nullable = false, length = 30)
    )
    private DeliveryPoint pickup;

    @Embedded
    @AttributeOverride(
            name = "address.value",
            column = @Column(name = "delivery_address", nullable = false, length = 255)
    )
    @AttributeOverride(
            name = "phoneNumber.value",
            column = @Column(name = "delivery_phone_number", nullable = false, length = 30)
    )
    private DeliveryPoint destination;

    protected DeliveryRoute() {
    }

    private DeliveryRoute(final DeliveryPoint pickup, final DeliveryPoint destination) {
        if (pickup == null || destination == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        this.pickup = pickup;
        this.destination = destination;
    }

    public static DeliveryRoute of(final DeliveryPoint pickup, final DeliveryPoint destination) {
        return new DeliveryRoute(pickup, destination);
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof DeliveryRoute that)) {
            return false;
        }
        return Objects.equals(pickup, that.pickup) && Objects.equals(destination, that.destination);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pickup, destination);
    }
}
