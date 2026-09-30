package setty.delivery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

/**
 * 주문 취소가 배송에서 확정됐다는 내부 기록. 기사·고객에게 노출되지 않는다.
 * 배송 요청이 만들어지기 전에 취소된 주문도 기록해, 늦게 도착한 배차 요청이 배송을 만들지 못하게 막는다.
 */
@Getter
@Entity
@Table(name = "delivery_cancellation")
public class DeliveryCancellation {

    @Getter(AccessLevel.NONE)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Embedded
    private OrderId orderId;

    @Getter(AccessLevel.NONE)
    @Column(name = "delivery_id")
    private Long deliveryId;

    @Column(name = "cancellation_request_id", nullable = false, length = 100)
    private String cancellationRequestId;

    @Column(name = "cancelled_at", nullable = false, updatable = false)
    private Instant cancelledAt;

    protected DeliveryCancellation() {
    }

    private DeliveryCancellation(
            final OrderId orderId,
            final DeliveryId deliveryId,
            final String cancellationRequestId,
            final Instant cancelledAt
    ) {
        if (orderId == null
                || cancellationRequestId == null
                || cancellationRequestId.isBlank()
                || cancelledAt == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        this.orderId = orderId;
        this.deliveryId = deliveryId == null ? null : deliveryId.value();
        this.cancellationRequestId = cancellationRequestId;
        this.cancelledAt = cancelledAt;
    }

    public static DeliveryCancellation of(
            final Delivery delivery,
            final String cancellationRequestId,
            final Instant cancelledAt
    ) {
        return new DeliveryCancellation(delivery.getOrderId(), delivery.getId(), cancellationRequestId, cancelledAt);
    }

    public static DeliveryCancellation beforeRequest(
            final OrderId orderId,
            final String cancellationRequestId,
            final Instant cancelledAt
    ) {
        return new DeliveryCancellation(orderId, null, cancellationRequestId, cancelledAt);
    }

    public DeliveryId getDeliveryId() {
        if (deliveryId == null) {
            return null;
        }
        return new DeliveryId(deliveryId);
    }
}
