package setty.delivery.application;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import setty.common.OrderRequested;
import setty.delivery.domain.Address;
import setty.delivery.domain.DeliveryRoute;
import setty.delivery.domain.EstimatedDeliveryFee;
import setty.delivery.domain.FurnitureInfo;
import setty.delivery.domain.OrderId;
import setty.delivery.domain.PhoneNumber;

/**
 * Delivery 컨텍스트가 반응하는 이벤트의 단일 진입 경계.
 * 외부 계약(common) 이벤트를 도메인 값으로 번역해 코어를 wire 스키마·transport에서 격리한다.
 */
@Component
@RequiredArgsConstructor
public class DeliveryEventListener {

    private static final System.Logger LOGGER = System.getLogger(DeliveryEventListener.class.getName());

    private final RegisterDeliveryService registerDeliveryService;
    private final DeliveryRequestNotifier deliveryRequestNotifier;

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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(final DeliveryRequestsChanged event) {
        try {
            deliveryRequestNotifier.notifyRequestsChanged();
        } catch (final RuntimeException exception) {
            LOGGER.log(System.Logger.Level.WARNING, "배송 요청 SSE 알림 전송에 실패했습니다.", exception);
        }
    }
}
