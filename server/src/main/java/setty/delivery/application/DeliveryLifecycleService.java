package setty.delivery.application;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.common.DeliveryStatus;
import setty.common.DeliveryStatusChanged;
import setty.delivery.domain.Delivery;
import setty.delivery.domain.DeliveryId;
import setty.delivery.domain.DriverId;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

@Service
@RequiredArgsConstructor
@Transactional
public class DeliveryLifecycleService {

    private final DeliveryRepository deliveryRepository;
    private final ApplicationEventPublisher eventPublisher;

    public void accept(final DeliveryId deliveryId, final DriverId driverId, final Instant acceptedAt) {
        final Delivery delivery = findDelivery(deliveryId);
        delivery.accept(driverId, acceptedAt);
        saveAndPublish(delivery, DeliveryStatus.ACCEPTED, acceptedAt);
        eventPublisher.publishEvent(new DeliveryRequestsChanged());
    }

    public void pickUp(final DeliveryId deliveryId, final DriverId driverId, final Instant pickedUpAt) {
        final Delivery delivery = findDelivery(deliveryId);
        delivery.pickUp(driverId, pickedUpAt);
        saveAndPublish(delivery, DeliveryStatus.PICKED_UP, pickedUpAt);
    }

    public void complete(final DeliveryId deliveryId, final DriverId driverId, final Instant deliveredAt) {
        final Delivery delivery = findDelivery(deliveryId);
        delivery.complete(driverId, deliveredAt);
        saveAndPublish(delivery, DeliveryStatus.DELIVERED, deliveredAt);
    }

    private Delivery findDelivery(final DeliveryId deliveryId) {
        if (deliveryId == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_NOT_FOUND));
    }

    private void saveAndPublish(
            final Delivery delivery,
            final DeliveryStatus status,
            final Instant changedAt
    ) {
        deliveryRepository.save(delivery);
        eventPublisher.publishEvent(new DeliveryStatusChanged(
                delivery.getId().value(),
                delivery.getOrderId().value(),
                status.name(),
                changedAt
        ));
    }
}
