package setty.delivery.event;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import setty.common.OrderCancellationRequested;
import setty.common.OrderRequested;
import setty.delivery.application.DeliveryLifecycleService;
import setty.delivery.application.RegisterDeliveryService;
import setty.delivery.domain.Address;
import setty.delivery.domain.DeliveryRoute;
import setty.delivery.domain.EstimatedDeliveryFee;
import setty.delivery.domain.FurnitureInfo;
import setty.delivery.domain.OrderId;
import setty.delivery.domain.PhoneNumber;

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
                new OrderId(event.orderId()),
                new FurnitureInfo(event.itemName(), event.category()),
                new DeliveryRoute(
                        new Address(event.pickupAddress()),
                        new Address(event.deliveryAddress()),
                        new PhoneNumber(event.pickupPhoneNumber()),
                        new PhoneNumber(event.deliveryPhoneNumber())
                ),
                new EstimatedDeliveryFee(event.deliveryFee()),
                Instant.now()
        );
    }

    @EventListener
    public void handle(final OrderCancellationRequested event) {
        deliveryLifecycleService.cancel(
                new OrderId(event.orderId()),
                event.cancellationRequestId(),
                Instant.now()
        );
    }
}
