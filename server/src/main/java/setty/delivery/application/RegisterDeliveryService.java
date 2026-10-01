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
import setty.delivery.persistence.DeliveryOrderDecisionRepository;
import setty.delivery.persistence.DeliveryRepository;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

@Service
@RequiredArgsConstructor
public class RegisterDeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryOrderDecisionRepository decisionRepository;
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

        // 이미 등록됐거나 취소가 먼저 판정된 주문이면 재발행된 배송 요청이라도 만들지 않는다.
        if (!decisionRepository.decideRequested(orderId, requestedAt)) {
            return;
        }

        final Delivery delivery = Delivery.request(orderId, furniture, route, fee, requestedAt);
        deliveryRepository.save(delivery);
        eventPublisher.publishEvent(new DeliveryRequestsChanged());
    }
}
