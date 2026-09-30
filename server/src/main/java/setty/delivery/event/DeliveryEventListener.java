package setty.delivery.event;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import setty.common.OrderCancellationRequested;
import setty.common.OrderRequested;
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
 */
@Component
@RequiredArgsConstructor
public class DeliveryEventListener {

    private final RegisterDeliveryService registerDeliveryService;
    private final DeliveryLifecycleService deliveryLifecycleService;

    @EventListener
    public void handle(final OrderRequested event) {
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

    @EventListener
    public void handle(final OrderCancellationRequested event) {
        deliveryLifecycleService.cancel(
                OrderId.from(event.orderId()),
                event.cancellationRequestId(),
                Instant.now()
        );
    }
}
