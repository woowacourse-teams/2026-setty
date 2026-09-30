package setty.delivery.application;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import setty.delivery.domain.OrderId;
import setty.delivery.domain.delivery.Delivery;
import setty.delivery.domain.delivery.DeliveryRoute;
import setty.delivery.domain.delivery.EstimatedDeliveryFee;
import setty.delivery.domain.delivery.FurnitureInfo;
import setty.delivery.persistence.DeliveryRepository;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

@Service
@RequiredArgsConstructor
public class RegisterDeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final ApplicationEventPublisher eventPublisher;

    // 발행한 쪽 트랜잭션이 커밋된 뒤 호출되므로 배송만의 새 트랜잭션에서 처리한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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

        // 재전달된 배송 요청이면 다시 만들지 않는다.
        if (deliveryRepository.existsByOrderId(orderId)) {
            return;
        }

        final Delivery delivery = Delivery.request(orderId, furniture, route, fee, requestedAt);
        deliveryRepository.save(delivery);
        eventPublisher.publishEvent(new DeliveryRequestsChanged());
    }
}
