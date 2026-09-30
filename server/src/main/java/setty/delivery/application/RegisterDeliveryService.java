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
import setty.delivery.persistence.DeliveryCancellationRepository;
import setty.delivery.persistence.DeliveryOrderLockRepository;
import setty.delivery.persistence.DeliveryRepository;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

@Service
@RequiredArgsConstructor
public class RegisterDeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryCancellationRepository cancellationRepository;
    private final DeliveryOrderLockRepository deliveryOrderLockRepository;
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

        // 같은 주문의 배차 요청·취소 요청과 한 번에 하나씩 처리되도록 주문 단위로 잠근다.
        // 배송과 취소 기록은 다른 테이블이라 UNIQUE 제약만으로는 동시에 들어온 둘을 막지 못한다.
        // 락은 이 트랜잭션이 끝날 때 풀린다.
        deliveryOrderLockRepository.lock(orderId);
        // 재전달된 배차 요청이거나, 배차 요청보다 취소가 먼저 확정된 주문이면 배송을 만들지 않는다.
        if (deliveryRepository.existsByOrderId(orderId) || cancellationRepository.existsByOrderId(orderId)) {
            return;
        }

        final Delivery delivery = Delivery.request(orderId, furniture, route, fee, requestedAt);
        deliveryRepository.save(delivery);
        eventPublisher.publishEvent(new DeliveryRequestsChanged());
    }
}
