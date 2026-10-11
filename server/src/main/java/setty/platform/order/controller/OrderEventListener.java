package setty.platform.order.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import setty.common.DeliveryAccepted;
import setty.common.DeliveryCancelled;
import setty.common.DeliveryCancellationRejected;
import setty.common.DeliveryDelivered;
import setty.common.DeliveryPickedUp;
import setty.common.DeliveryStatus;
import setty.common.PaymentCompleted;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.platform.order.service.OrderService;
import setty.platform.order.service.SyncOrderDeliveryStatusService;

/**
 * 다른 모듈에서 주문으로 들어오는 이벤트의 단일 진입 경계.
 * 발행한 쪽이 커밋된 뒤 주문의 새 트랜잭션에서 처리하므로 주문 처리 실패가 결제·배송을 롤백하지 않는다.
 * 실패한 이벤트는 발행 기록(EVENT_PUBLICATION)에 남아 재발행되므로 각 처리는 멱등해야 한다.
 */
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final OrderService orderService;
    private final SyncOrderDeliveryStatusService syncOrderDeliveryStatusService;

    // 결제 완료 주문을 확정하고 배송 요청 이벤트를 발행한다.
    @ApplicationModuleListener
    public void onPaymentCompleted(final PaymentCompleted event) {
        requireEvent(event);
        orderService.publishOrderConfirmed(event.orderId());
    }

    // 배송 취소 성공 응답으로 주문을 취소 확정하고 OrderCancelled를 발행한다.
    @ApplicationModuleListener
    public void onDeliveryCancelled(final DeliveryCancelled event) {
        requireEvent(event);
        orderService.confirmCancellation(
                event.orderId(),
                event.cancellationRequestId(),
                event.decidedAt()
        );
    }

    // 배송 취소 거절 응답으로 주문을 CONFIRMED 상태로 되돌린다.
    @ApplicationModuleListener
    public void onDeliveryCancellationRejected(final DeliveryCancellationRejected event) {
        requireEvent(event);
        orderService.rejectCancellation(
                event.orderId(),
                event.cancellationRequestId(),
                event.decidedAt()
        );
    }

    // 기사 수락 사실을 주문의 배송 상태에 반영한다.
    @ApplicationModuleListener
    public void onDeliveryAccepted(final DeliveryAccepted event) {
        requireEvent(event);
        syncOrderDeliveryStatusService.sync(
                event.deliveryId(),
                event.orderId(),
                event.changedAt(),
                DeliveryStatus.ACCEPTED
        );
    }

    // 기사 픽업 사실을 주문의 배송 상태에 반영한다.
    @ApplicationModuleListener
    public void onDeliveryPickedUp(final DeliveryPickedUp event) {
        requireEvent(event);
        syncOrderDeliveryStatusService.sync(
                event.deliveryId(),
                event.orderId(),
                event.changedAt(),
                DeliveryStatus.PICKED_UP
        );
    }

    // 배송 완료 사실을 주문의 배송 상태에 반영한다.
    @ApplicationModuleListener
    public void onDeliveryDelivered(final DeliveryDelivered event) {
        requireEvent(event);
        syncOrderDeliveryStatusService.sync(
                event.deliveryId(),
                event.orderId(),
                event.changedAt(),
                DeliveryStatus.DELIVERED
        );
    }

    private static void requireEvent(final Object event) {
        if (event == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }
}
