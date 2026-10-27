package setty.platform.order.domain;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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

    private static final Duration DEFAULT_PENDING_TIMEOUT = Duration.ofMinutes(6);
    public static final Duration COMPLETION_WAITING_PERIOD = Duration.ofDays(3);

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

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false, length = 20)
    private OrderStatus orderStatus;

    @Column(name = "cancellation_request_id", length = 36)
    private String cancellationRequestId;

    @Column(name = "driver_id")
    private Long driverId;

    @Column(name = "pending_expires_at")
    private Instant pendingExpiresAt;

    // 배송 모듈의 이벤트로 동기화된 시각. DELIVERED는 마지막 배송 상태라 배송 완료 후에는 배송 완료 시각으로 고정된다.
    @Column(name = "delivery_status_changed_at")
    private Instant deliveryStatusChangedAt;

    // 주문 시점 값. 이후 회원정보·매물 배송비가 바뀌어도 결제·배송·정산은 이 값을 쓴다. 도입 전 주문은 NULL이다.
    @Column(name = "delivery_fee", updatable = false)
    private Integer deliveryFee;

    @Embedded
    @AttributeOverride(name = "address",
            column = @Column(name = "pickup_address", length = 200, updatable = false))
    @AttributeOverride(name = "phoneNumber",
            column = @Column(name = "pickup_phone_number", length = 13, updatable = false))
    @AttributeOverride(name = "dongCode",
            column = @Column(name = "pickup_dong_code", length = 10, updatable = false))
    @AttributeOverride(name = "zipCode",
            column = @Column(name = "pickup_zip_code", length = 5, updatable = false))
    @AttributeOverride(name = "managementNumber",
            column = @Column(name = "pickup_address_mgmt_no", length = 25, updatable = false))
    private OrderPlace pickup;

    @Embedded
    @AttributeOverride(name = "address",
            column = @Column(name = "delivery_address", length = 200, updatable = false))
    @AttributeOverride(name = "phoneNumber",
            column = @Column(name = "delivery_phone_number", length = 13, updatable = false))
    @AttributeOverride(name = "dongCode",
            column = @Column(name = "delivery_dong_code", length = 10, updatable = false))
    @AttributeOverride(name = "zipCode",
            column = @Column(name = "delivery_zip_code", length = 5, updatable = false))
    @AttributeOverride(name = "managementNumber",
            column = @Column(name = "delivery_address_mgmt_no", length = 25, updatable = false))
    private OrderPlace destination;

    protected Order() {
    }

    public Order(final Long listingId, final Long buyerId) {
        this.listingId = listingId;
        this.buyerId = buyerId;
        this.deliveryStatus = DeliveryStatus.REQUESTED;
        this.orderStatus = OrderStatus.CONFIRMED;
    }

    public boolean requestDelivery() {
        if (this.orderStatus == OrderStatus.CONFIRMED) {
            return false;
        }
        if (this.orderStatus != OrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        }
        if (this.deliveryStatus != DeliveryStatus.PENDING) {
            throw new BusinessException(ErrorCode.ORDER_DELIVERY_STATUS_MISMATCH);
        }
        this.deliveryStatus = DeliveryStatus.REQUESTED;
        this.orderStatus = OrderStatus.CONFIRMED;
        return true;
    }

    public boolean requestCancellation(final String cancellationRequestId) {
        if (this.orderStatus == OrderStatus.CANCEL_PENDING) {
            return false;
        }
        if (this.orderStatus != OrderStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        }
        if (cancellationRequestId == null || cancellationRequestId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        this.orderStatus = OrderStatus.CANCEL_PENDING;
        this.cancellationRequestId = cancellationRequestId;
        return true;
    }

    public boolean confirmCancellation(final String cancellationRequestId) {
        if (!isCurrentCancellationRequest(cancellationRequestId)
                || this.orderStatus != OrderStatus.CANCEL_PENDING) {
            return false;
        }
        this.orderStatus = OrderStatus.CANCELLED;
        return true;
    }

    public boolean rejectCancellation(final String cancellationRequestId) {
        if (!isCurrentCancellationRequest(cancellationRequestId)
                || this.orderStatus != OrderStatus.CANCEL_PENDING) {
            return false;
        }
        this.orderStatus = OrderStatus.CONFIRMED;
        return true;
    }

    private boolean isCurrentCancellationRequest(final String cancellationRequestId) {
        return cancellationRequestId != null
                && cancellationRequestId.equals(this.cancellationRequestId);
    }

    // 결제 대기 주문 — 배송이 시작되지 않았으므로 OrderConfirmed를 발행하지 않는 경로에서만 쓴다.
    public static Order pending(final Long listingId, final Long buyerId) {
        return pending(listingId, buyerId, Instant.now().plus(DEFAULT_PENDING_TIMEOUT));
    }

    public static Order pending(final Long listingId, final Long buyerId, final Instant pendingExpiresAt) {
        final Order order = new Order(listingId, buyerId);
        order.deliveryStatus = DeliveryStatus.PENDING;
        order.orderStatus = OrderStatus.PENDING;
        order.pendingExpiresAt = pendingExpiresAt;
        return order;
    }

    public static Order pending(
            final Long listingId,
            final Long buyerId,
            final Instant pendingExpiresAt,
            final int deliveryFee,
            final OrderPlace pickup,
            final OrderPlace destination
    ) {
        final Order order = pending(listingId, buyerId, pendingExpiresAt);
        order.deliveryFee = deliveryFee;
        order.pickup = pickup;
        order.destination = destination;
        return order;
    }

    public boolean canExpire(final Instant referenceTime) {
        return orderStatus == OrderStatus.PENDING
                && pendingExpiresAt != null
                && referenceTime != null
                && !pendingExpiresAt.isAfter(referenceTime);
    }

    public boolean isPayable(final Instant referenceTime) {
        return orderStatus == OrderStatus.PENDING && !canExpire(referenceTime);
    }

    public boolean expire(final Instant referenceTime) {
        if (!canExpire(referenceTime)) {
            return false;
        }
        orderStatus = OrderStatus.EXPIRED;
        return true;
    }

    // 직전 상태에서 한 단계 전진만 허용,
    // 같은 상태 중복 이벤트는 무시(멱등), 그 외 불일치는 예외 — 버그를 조용히 삼키지 않는다.
    public void syncDeliveryStatus(final DeliveryStatus newStatus, final Instant changedAt) {
        if (this.deliveryStatus == newStatus) {
            // 배차 취소 뒤 새 기사의 수락이 취소보다 먼저 도착한 경우다. 수락 시각을 새 배차로 바꿔 늦게 온 취소가 되돌리지 못하게 한다.
            if (newStatus == DeliveryStatus.ACCEPTED && changedAt != null) {
                this.deliveryStatusChangedAt = changedAt;
            }
            return;
        }
        if (this.deliveryStatus != expectedPreviousStatusOf(newStatus)) {
            throw new BusinessException(ErrorCode.ORDER_DELIVERY_STATUS_MISMATCH);
        }
        if (changedAt == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        this.deliveryStatus = newStatus;
        this.deliveryStatusChangedAt = changedAt;
    }

    // 이벤트는 순서가 바뀌어 도착할 수 있어 시각 크기가 아니라 같은 배차인지로 판단한다. 다르면 이미 새 배차가 반영된 것이다.
    public boolean reopenDelivery(final Instant cancelledAcceptedAt, final Instant changedAt) {
        if (cancelledAcceptedAt == null || changedAt == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (deliveryStatus != DeliveryStatus.ACCEPTED || !cancelledAcceptedAt.equals(deliveryStatusChangedAt)) {
            return false;
        }
        this.deliveryStatus = DeliveryStatus.REQUESTED;
        this.deliveryStatusChangedAt = changedAt;
        return true;
    }

    // 배송 완료된 주문만 판매 완료할 수 있다. 이미 판매 완료된 주문이면 false.
    public boolean complete() {
        if (orderStatus == OrderStatus.COMPLETED) {
            return false;
        }
        if (orderStatus != OrderStatus.CONFIRMED || deliveryStatus != DeliveryStatus.DELIVERED) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        }
        this.orderStatus = OrderStatus.COMPLETED;
        return true;
    }

    // 취소 대기 중인 주문은 취소 결과가 나와 CONFIRMED로 돌아온 뒤에 자동 확정한다.
    public boolean canAutoComplete(final Instant referenceTime) {
        return orderStatus == OrderStatus.CONFIRMED
                && deliveryStatus == DeliveryStatus.DELIVERED
                && deliveryStatusChangedAt != null
                && referenceTime != null
                && !deliveryStatusChangedAt.plus(COMPLETION_WAITING_PERIOD).isAfter(referenceTime);
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

    public OrderStatus getOrderStatus() {
        return orderStatus;
    }

    public String getCancellationRequestId() {
        return cancellationRequestId;
    }

    public Long getDriverId() {
        return driverId;
    }

    public Instant getPendingExpiresAt() {
        return pendingExpiresAt;
    }

    public Instant getDeliveryStatusChangedAt() {
        return deliveryStatusChangedAt;
    }

    public Integer getDeliveryFee() {
        return deliveryFee;
    }

    // 모든 칸이 NULL인 도입 전 주문은 null이다.
    public OrderPlace getPickup() {
        return pickup;
    }

    public OrderPlace getDestination() {
        return destination;
    }
}
