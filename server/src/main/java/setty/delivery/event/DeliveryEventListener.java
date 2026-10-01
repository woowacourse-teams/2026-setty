package setty.delivery.event;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import setty.common.OrderCancellationRequested;
import setty.common.OrderConfirmed;
import setty.delivery.application.DeliveryLifecycleService;
import setty.delivery.application.RegisterDeliveryService;
import setty.delivery.domain.OrderId;
import setty.delivery.domain.delivery.DeliveryPoint;
import setty.delivery.domain.delivery.DeliveryRoute;
import setty.delivery.domain.delivery.EstimatedDeliveryFee;
import setty.delivery.domain.delivery.FurnitureInfo;

/**
 * 다른 컨텍스트에서 Delivery로 들어오는 이벤트의 단일 진입 경계.
 * 외부 계약(common) 이벤트를 도메인 값으로 번역해 코어를 wire 스키마·transport에서 격리한다.
 *
 * <p>발행한 쪽 트랜잭션이 커밋된 뒤 배송의 새 트랜잭션에서 처리한다. 배송 처리 실패는 발행한 쪽을 롤백하지 않는다.
 * 예외를 삼키지 않아야 실패한 이벤트가 발행 기록(EVENT_PUBLICATION)에 남아 재발행된다.
 */
@Component
@RequiredArgsConstructor
public class DeliveryEventListener {

    private final RegisterDeliveryService registerDeliveryService;
    private final DeliveryLifecycleService deliveryLifecycleService;

    @ApplicationModuleListener
    public void handle(final OrderConfirmed event) {
        registerDeliveryService.register(
                OrderId.from(event.orderId()),
                FurnitureInfo.of(event.itemName(), event.category()),
                DeliveryRoute.of(
                        DeliveryPoint.pickup(event.pickupAddress(), event.pickupPhoneNumber()),
                        DeliveryPoint.destination(event.deliveryAddress(), event.deliveryPhoneNumber())
                ),
                EstimatedDeliveryFee.from(event.deliveryFee()),
                Instant.now()
        );
    }

    @ApplicationModuleListener
    public void handle(final OrderCancellationRequested event) {
        deliveryLifecycleService.cancel(
                OrderId.from(event.orderId()),
                event.cancellationRequestId(),
                Instant.now()
        );
    }
}
