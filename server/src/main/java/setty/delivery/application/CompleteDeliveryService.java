package setty.delivery.application;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.common.DeliveryStatus;
import setty.delivery.application.event.DeliveryStatusChanged;
import setty.delivery.domain.Delivery;
import setty.delivery.domain.DeliveryId;
import setty.delivery.domain.DriverId;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

@Service
@RequiredArgsConstructor
public class CompleteDeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void complete(final DeliveryId deliveryId, final DriverId driverId, final Instant deliveredAt) {
        final Delivery delivery = findDelivery(deliveryId);
        delivery.complete(driverId, deliveredAt);
        deliveryRepository.save(delivery);
        eventPublisher.publishEvent(new DeliveryStatusChanged(
                delivery.getId().value(),
                delivery.getOrderId().value(),
                DeliveryStatus.DELIVERED.name(),
                deliveredAt
        ));
    }

    private Delivery findDelivery(final DeliveryId deliveryId) {
        if (deliveryId == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_NOT_FOUND));
    }
}
