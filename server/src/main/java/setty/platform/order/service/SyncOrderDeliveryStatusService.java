package setty.platform.order.service;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    public void sync(
            final Long deliveryId,
            final Long orderId,
            final Instant changedAt,
            final DeliveryStatus newStatus
    ) {
        validateEvent(deliveryId, orderId, changedAt, newStatus);
        final Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        order.syncDeliveryStatus(newStatus, changedAt);
    }

    private void validateEvent(
            final Long deliveryId,
            final Long orderId,
            final Instant changedAt,
            final DeliveryStatus newStatus
    ) {
        if (deliveryId == null || deliveryId <= 0
                || orderId == null || orderId <= 0
                || changedAt == null || newStatus == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }
}
