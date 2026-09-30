package setty.delivery.event;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
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
 *
 * <p>발행한 쪽 트랜잭션이 커밋된 뒤 배송의 새 트랜잭션에서 처리한다. 배송 처리 실패는 발행한 쪽을 롤백하지 않으며,
 * 이미 커밋된 호출자에게 예외가 전파되지 않도록 기록만 남긴다.
 */
@Component
@RequiredArgsConstructor
public class DeliveryEventListener {

    private static final System.Logger LOGGER = System.getLogger(DeliveryEventListener.class.getName());

    private final RegisterDeliveryService registerDeliveryService;
    private final DeliveryLifecycleService deliveryLifecycleService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(final OrderRequested event) {
        try {
            register(event);
        } catch (final RuntimeException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "배차 요청 처리에 실패했습니다. orderId=" + event.orderId(), exception);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(final OrderCancellationRequested event) {
        try {
            deliveryLifecycleService.cancel(
                    OrderId.from(event.orderId()),
                    event.cancellationRequestId(),
                    Instant.now()
            );
        } catch (final RuntimeException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "주문 취소 요청 처리에 실패했습니다. orderId=" + event.orderId(), exception);
        }
    }

    private void register(final OrderRequested event) {
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
}
