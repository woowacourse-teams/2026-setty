package setty.platform.order.service;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.common.DeliveryAccepted;
import setty.common.DeliveryDelivered;
import setty.common.DeliveryPickedUp;
import setty.common.DeliveryStatus;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.platform.order.domain.Order;
import setty.platform.order.repository.OrderRepository;

@Service
public class SyncOrderDeliveryStatusService {

    private final OrderRepository orderRepository;

    public SyncOrderDeliveryStatusService(final OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public void sync(final DeliveryAccepted event) {
        if (event == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        sync(event.deliveryId(), event.orderId(), event.changedAt(), DeliveryStatus.ACCEPTED);
    }

    @Transactional
    public void sync(final DeliveryPickedUp event) {
        if (event == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        sync(event.deliveryId(), event.orderId(), event.changedAt(), DeliveryStatus.PICKED_UP);
    }

    @Transactional
    public void sync(final DeliveryDelivered event) {
        if (event == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        sync(event.deliveryId(), event.orderId(), event.changedAt(), DeliveryStatus.DELIVERED);
    }

    private void sync(
            final Long deliveryId,
            final Long orderId,
            final Instant changedAt,
            final DeliveryStatus newStatus
    ) {
        validateEvent(deliveryId, orderId, changedAt);
        final Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        order.syncDeliveryStatus(newStatus);
    }

    private void validateEvent(final Long deliveryId, final Long orderId, final Instant changedAt) {
        if (deliveryId == null || deliveryId <= 0
                || orderId == null || orderId <= 0
                || changedAt == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }
}
