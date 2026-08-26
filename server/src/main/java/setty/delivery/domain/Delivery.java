package setty.delivery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import setty.common.DeliveryStatus;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

@Entity
@Table(name = "delivery")
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Embedded
    private OrderId orderId;

    @Embedded
    private FurnitureInfo furnitureInfo;

    @Embedded
    private DeliveryRoute route;

    @Embedded
    private EstimatedDeliveryFee estimatedDeliveryFee;

    @Embedded
    private DeliveryAssignment assignment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryStatus status;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "picked_up_at")
    private Instant pickedUpAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    protected Delivery() {
    }

    private Delivery(
            final OrderId orderId,
            final FurnitureInfo furnitureInfo,
            final DeliveryRoute route,
            final EstimatedDeliveryFee estimatedDeliveryFee,
            final Instant requestedAt
    ) {
        if (orderId == null
                || furnitureInfo == null
                || route == null
                || estimatedDeliveryFee == null
                || requestedAt == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        this.orderId = orderId;
        this.furnitureInfo = furnitureInfo;
        this.route = route;
        this.estimatedDeliveryFee = estimatedDeliveryFee;
        this.status = DeliveryStatus.REQUESTED;
        this.requestedAt = requestedAt;
    }

    public static Delivery request(
            final OrderId orderId,
            final FurnitureInfo furnitureInfo,
            final DeliveryRoute route,
            final EstimatedDeliveryFee estimatedDeliveryFee,
            final Instant requestedAt
    ) {
        return new Delivery(orderId, furnitureInfo, route, estimatedDeliveryFee, requestedAt);
    }

    public void accept(final DriverId driverId, final Instant acceptedAt) {
        if (status != DeliveryStatus.REQUESTED) {
            throw new BusinessException(ErrorCode.DELIVERY_ALREADY_ACCEPTED);
        }
        assignment = new DeliveryAssignment(driverId, acceptedAt);
        status = DeliveryStatus.ACCEPTED;
    }

    public void pickUp(final DriverId driverId, final Instant pickedUpAt) {
        ensureStatus(DeliveryStatus.ACCEPTED);
        ensureAssignedDriver(driverId);
        ensureTimePresent(pickedUpAt);
        this.pickedUpAt = pickedUpAt;
        status = DeliveryStatus.PICKED_UP;
    }

    public void complete(final DriverId driverId, final Instant deliveredAt) {
        ensureStatus(DeliveryStatus.PICKED_UP);
        ensureAssignedDriver(driverId);
        ensureTimePresent(deliveredAt);
        this.deliveredAt = deliveredAt;
        status = DeliveryStatus.DELIVERED;
    }

    private void ensureStatus(final DeliveryStatus expectedStatus) {
        if (status != expectedStatus) {
            throw new BusinessException(ErrorCode.INVALID_DELIVERY_TRANSITION);
        }
    }

    private void ensureAssignedDriver(final DriverId driverId) {
        if (driverId == null || assignment == null || !assignment.isAssignedTo(driverId)) {
            throw new BusinessException(ErrorCode.DELIVERY_DRIVER_MISMATCH);
        }
    }

    private void ensureTimePresent(final Instant occurredAt) {
        if (occurredAt == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    public DeliveryId getId() {
        if (id == null) {
            return null;
        }
        return new DeliveryId(id);
    }

    public OrderId getOrderId() {
        return orderId;
    }

    public FurnitureInfo getFurnitureInfo() {
        return furnitureInfo;
    }

    public DeliveryRoute getRoute() {
        return route;
    }

    public EstimatedDeliveryFee getEstimatedDeliveryFee() {
        return estimatedDeliveryFee;
    }

    public DeliveryAssignment getAssignment() {
        return assignment;
    }

    public DriverId getDriverId() {
        if (assignment == null) {
            return null;
        }
        return assignment.driverId();
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getAcceptedAt() {
        if (assignment == null) {
            return null;
        }
        return assignment.acceptedAt();
    }

    public Instant getPickedUpAt() {
        return pickedUpAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }
}
