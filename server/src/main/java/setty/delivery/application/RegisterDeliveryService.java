package setty.delivery.application;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.delivery.domain.OrderId;
import setty.delivery.domain.delivery.Delivery;
import setty.delivery.domain.delivery.DeliveryRoute;
import setty.delivery.domain.delivery.EstimatedDeliveryFee;
import setty.delivery.domain.delivery.FurnitureInfo;
import setty.delivery.persistence.DeliveryCancellationRepository;
import setty.delivery.persistence.DeliveryRepository;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

@Service
@RequiredArgsConstructor
public class RegisterDeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryCancellationRepository cancellationRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void register(
            final OrderId orderId,
            final FurnitureInfo furniture,
            final DeliveryRoute route,
            final EstimatedDeliveryFee fee,
            final Instant requestedAt
    ) {
        if (orderId == null || furniture == null || route == null || fee == null || requestedAt == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        // 재전달된 배차 요청이거나, 배차 요청보다 취소가 먼저 확정된 주문이면 배송을 만들지 않는다.
        if (deliveryRepository.existsByOrderId(orderId) || cancellationRepository.existsByOrderId(orderId)) {
            return;
        }

        final Delivery delivery = Delivery.request(orderId, furniture, route, fee, requestedAt);
        deliveryRepository.save(delivery);
        eventPublisher.publishEvent(new DeliveryRequestsChanged());
    }
}
