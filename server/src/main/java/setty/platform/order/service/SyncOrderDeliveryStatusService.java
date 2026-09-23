package setty.platform.order.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.common.DeliveryStatus;
import setty.common.DeliveryStatusChanged;
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
    public void sync(final DeliveryStatusChanged event) {
        validateEvent(event);
        final DeliveryStatus newStatus = parseStatus(event.status());
        final Order order = orderRepository.findByIdForUpdate(event.orderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        order.syncDeliveryStatus(newStatus);
    }

    private void validateEvent(final DeliveryStatusChanged event) {
        if (event == null
                || event.deliveryId() == null || event.deliveryId() <= 0
                || event.orderId() == null || event.orderId() <= 0
                || event.changedAt() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private DeliveryStatus parseStatus(final String status) {
        if (status == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return switch (status) {
            case "ACCEPTED" -> DeliveryStatus.ACCEPTED;
            case "PICKED_UP" -> DeliveryStatus.PICKED_UP;
            case "DELIVERED" -> DeliveryStatus.DELIVERED;
            default -> throw new BusinessException(ErrorCode.INVALID_REQUEST);
        };
    }
}
