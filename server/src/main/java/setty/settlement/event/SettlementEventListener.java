package setty.settlement.event;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import setty.common.DeliveryDelivered;
import setty.common.OrderCancelled;
import setty.common.OrderCompleted;
import setty.common.OrderConfirmed;
import setty.settlement.application.SettlementService;

/**
 * 다른 모듈에서 정산으로 들어오는 이벤트의 단일 진입 경계. 정산은 이벤트로 받은 값만 쓰고 다른 모듈의 테이블을 읽지 않는다.
 * 실패한 이벤트는 재발행되고 이벤트 간 순서도 보장되지 않으므로 처리는 멱등하고 순서와 무관하다.
 */
@Component
@RequiredArgsConstructor
public class SettlementEventListener {

    private final SettlementService settlementService;

    @ApplicationModuleListener
    public void handle(final OrderConfirmed event) {
        settlementService.recordSeller(
                event.orderId(),
                event.sellerId(),
                event.listingId(),
                event.itemPrice(),
                Instant.now()
        );
    }

    @ApplicationModuleListener
    public void handle(final DeliveryDelivered event) {
        settlementService.recordDriver(event.orderId(), event.driverId(), event.deliveryFee(), Instant.now());
    }

    @ApplicationModuleListener
    public void handle(final OrderCompleted event) {
        settlementService.complete(event.orderId(), event.completedAt());
    }

    @ApplicationModuleListener
    public void handle(final OrderCancelled event) {
        settlementService.cancel(event.orderId(), event.cancelledAt());
    }
}
