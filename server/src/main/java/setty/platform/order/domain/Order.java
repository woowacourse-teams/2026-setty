package setty.platform.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import setty.common.DeliveryStatus;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

@Entity
@Table(name = "orders")
public class Order {

    private static final Duration DEFAULT_PENDING_TIMEOUT = Duration.ofMinutes(10);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 20)
    private DeliveryStatus deliveryStatus;

    @Column(name = "driver_id")
    private Long driverId;

    @Column(name = "pending_expires_at")
    private Instant pendingExpiresAt;

    protected Order() {
    }

    public Order(final Long listingId, final Long buyerId) {
        this.listingId = listingId;
        this.buyerId = buyerId;
        this.deliveryStatus = DeliveryStatus.REQUESTED;
    }

    public boolean requestDelivery() {
        if (this.deliveryStatus == DeliveryStatus.REQUESTED) {
            return false;
        }
        if (this.deliveryStatus != DeliveryStatus.PENDING) {
            throw new BusinessException(ErrorCode.ORDER_DELIVERY_STATUS_MISMATCH);
        }
        this.deliveryStatus = DeliveryStatus.REQUESTED;
        return true;
    }

    // 결제 대기 주문 — 배송이 시작되지 않았으므로 OrderRequested를 발행하지 않는 경로에서만 쓴다.
    public static Order pending(final Long listingId, final Long buyerId) {
        return pending(listingId, buyerId, Instant.now().plus(DEFAULT_PENDING_TIMEOUT));
    }

    public static Order pending(final Long listingId, final Long buyerId, final Instant pendingExpiresAt) {
        final Order order = new Order(listingId, buyerId);
        order.deliveryStatus = DeliveryStatus.PENDING;
        order.pendingExpiresAt = pendingExpiresAt;
        return order;
    }

    public boolean canExpire(final Instant referenceTime) {
        return deliveryStatus == DeliveryStatus.PENDING
                && pendingExpiresAt != null
                && referenceTime != null
                && !pendingExpiresAt.isAfter(referenceTime);
    }

    // 직전 상태에서 한 단계 전진만 허용,
    // 같은 상태 중복 이벤트는 무시(멱등), 그 외 불일치는 예외 — 버그를 조용히 삼키지 않는다.
    public void syncDeliveryStatus(final DeliveryStatus newStatus) {
        if (this.deliveryStatus == newStatus) {
            return;
        }
        if (this.deliveryStatus != expectedPreviousStatusOf(newStatus)) {
            throw new BusinessException(ErrorCode.ORDER_DELIVERY_STATUS_MISMATCH);
        }
        this.deliveryStatus = newStatus;
    }

    private DeliveryStatus expectedPreviousStatusOf(final DeliveryStatus newStatus) {
        if (newStatus == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return switch (newStatus) {
            case ACCEPTED -> DeliveryStatus.REQUESTED;
            case PICKED_UP -> DeliveryStatus.ACCEPTED;
            case DELIVERED -> DeliveryStatus.PICKED_UP;
            case PENDING, REQUESTED -> throw new BusinessException(ErrorCode.INVALID_REQUEST);
        };
    }

    public Long getId() {
        return id;
    }

    public Long getListingId() {
        return listingId;
    }

    public Long getBuyerId() {
        return buyerId;
    }

    public DeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }

    public Long getDriverId() {
        return driverId;
    }

    public Instant getPendingExpiresAt() {
        return pendingExpiresAt;
    }
}
