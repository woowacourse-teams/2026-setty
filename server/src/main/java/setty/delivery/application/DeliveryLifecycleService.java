package setty.delivery.application;

import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import setty.common.DeliveryAccepted;
import setty.common.DeliveryCancellationRejected;
import setty.common.DeliveryCancelled;
import setty.common.DeliveryDelivered;
import setty.common.DeliveryPickedUp;
import setty.delivery.domain.DeliveryId;
import setty.delivery.domain.DriverId;
import setty.delivery.domain.OrderId;
import setty.delivery.domain.delivery.Delivery;
import setty.delivery.persistence.DeliveryRepository;
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
        deliveryRepository.save(delivery);
        eventPublisher.publishEvent(new DeliveryAccepted(deliveryId.value(), orderIdOf(delivery), acceptedAt));
        eventPublisher.publishEvent(new DeliveryRequestsChanged());
    }

    public void pickUp(final DeliveryId deliveryId, final DriverId driverId, final Instant pickedUpAt) {
        final Delivery delivery = findDelivery(deliveryId);
        delivery.pickUp(driverId, pickedUpAt);
        deliveryRepository.save(delivery);
        eventPublisher.publishEvent(new DeliveryPickedUp(deliveryId.value(), orderIdOf(delivery), pickedUpAt));
    }

    public void complete(final DeliveryId deliveryId, final DriverId driverId, final Instant deliveredAt) {
        final Delivery delivery = findDelivery(deliveryId);
        delivery.complete(driverId, deliveredAt);
        deliveryRepository.save(delivery);
        eventPublisher.publishEvent(new DeliveryDelivered(deliveryId.value(), orderIdOf(delivery), deliveredAt));
    }

    /**
     * 주문 취소 요청에 응답한다. 취소 가능 여부는 요청 접수 시점이 아니라 지금 배송 상태로 판단한다.
     * 같은 요청이 다시 와도 현재 상태에 따라 같은 결과를 다시 발행한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancel(final OrderId orderId, final String cancellationRequestId, final Instant decidedAt) {
        if (orderId == null || cancellationRequestId == null || cancellationRequestId.isBlank() || decidedAt == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        // 기사의 수락과 겹치지 않도록 배송 행을 잠근다.
        final Optional<Delivery> found = deliveryRepository.findByOrderIdForUpdate(orderId);
        if (found.isEmpty()) {
            publishCancelled(null, orderId, cancellationRequestId, decidedAt);
            return;
        }

        final Delivery delivery = found.get();
        if (delivery.isCancelled()) {
            publishCancelled(delivery.getId(), orderId, cancellationRequestId, decidedAt);
            return;
        }
        if (!delivery.isCancellable()) {
            eventPublisher.publishEvent(new DeliveryCancellationRejected(
                    delivery.getId().value(), orderId.value(), cancellationRequestId, decidedAt
            ));
            return;
        }

        delivery.cancel();
        deliveryRepository.save(delivery);
        publishCancelled(delivery.getId(), orderId, cancellationRequestId, decidedAt);
        eventPublisher.publishEvent(new DeliveryRequestsChanged());
    }

    private Delivery findDelivery(final DeliveryId deliveryId) {
        if (deliveryId == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        // 수락·픽업·완료가 서로, 그리고 취소와 겹치지 않도록 배송 행을 잠가 먼저 확정된 변경만 반영한다.
        return deliveryRepository.findByIdForUpdate(deliveryId.value())
                .orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_NOT_FOUND));
    }

    private void publishCancelled(
            final DeliveryId deliveryId,
            final OrderId orderId,
            final String cancellationRequestId,
            final Instant decidedAt
    ) {
        eventPublisher.publishEvent(new DeliveryCancelled(
                deliveryId == null ? null : deliveryId.value(),
                orderId.value(),
                cancellationRequestId,
                decidedAt
        ));
    }

    private static Long orderIdOf(final Delivery delivery) {
        return delivery.getOrderId().value();
    }
}
