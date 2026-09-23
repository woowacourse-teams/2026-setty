package setty.delivery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import java.time.Instant;
import java.util.Objects;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

@Embeddable
public class DeliveryAssignment {

    @Embedded
    private DriverId driverId;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    protected DeliveryAssignment() {
    }

    public DeliveryAssignment(final DriverId driverId, final Instant acceptedAt) {
        if (driverId == null || acceptedAt == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        this.driverId = driverId;
        this.acceptedAt = acceptedAt;
    }

    public boolean isAssignedTo(final DriverId driverId) {
        return this.driverId.equals(driverId);
    }

    public DriverId driverId() {
        return driverId;
    }

    public Instant acceptedAt() {
        return acceptedAt;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof DeliveryAssignment that)) {
            return false;
        }
        return Objects.equals(driverId, that.driverId) && Objects.equals(acceptedAt, that.acceptedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(driverId, acceptedAt);
    }
}
