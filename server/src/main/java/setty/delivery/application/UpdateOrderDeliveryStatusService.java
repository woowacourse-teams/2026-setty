package setty.delivery.application;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.common.DeliveryStatus;
import setty.delivery.application.event.DeliveryStatusChanged;
import setty.delivery.domain.DeliveryId;
import setty.delivery.domain.OrderDeliveryState;
import setty.delivery.domain.OrderId;
import setty.delivery.repository.OrderDeliveryStatusRepository;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

@Service
@RequiredArgsConstructor
public class UpdateOrderDeliveryStatusService {

    private final OrderDeliveryStatusRepository orderDeliveryStatusRepository;

    @EventListener
    @Transactional
    public void update(final DeliveryStatusChanged event) {
        validateEvent(event);
        final DeliveryStatus newStatus = parseStatus(event.status());
        final OrderId orderId = new OrderId(event.orderId());
        final OrderDeliveryState orderDeliveryState = orderDeliveryStatusRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (orderDeliveryState.synchronizeTo(newStatus)) {
            orderDeliveryStatusRepository.save(orderDeliveryState);
        }
    }

    private void validateEvent(final DeliveryStatusChanged event) {
        if (event == null || event.changedAt() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        new DeliveryId(event.deliveryId());
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
