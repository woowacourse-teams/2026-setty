package setty.platform.order.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import setty.common.DeliveryAccepted;
import setty.common.DeliveryCancelled;
import setty.common.DeliveryCancellationRejected;
import setty.common.DeliveryDelivered;
import setty.common.DeliveryPickedUp;
import setty.common.PaymentCompleted;
import setty.common.PaymentFailed;
import setty.platform.order.service.OrderService;
import setty.platform.order.service.SyncOrderDeliveryStatusService;

@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final OrderService orderService;
    private final SyncOrderDeliveryStatusService syncOrderDeliveryStatusService;

    // 결제 완료 주문을 확정하고 배송 요청 이벤트를 발행한다.
    @EventListener
    public void onPaymentCompleted(final PaymentCompleted event) {
        orderService.publishOrderConfirmed(event.orderId());
    }

    // 결제 실패 주문을 제거하고 매물 선점을 해제한다.
    @EventListener
    public void onPaymentFailed(final PaymentFailed event) {
        orderService.cancelPending(event.orderId());
    }

    // 배송 취소 성공 응답으로 주문을 취소 확정하고 OrderCancelled를 발행한다.
    @EventListener
    public void onDeliveryCancelled(final DeliveryCancelled event) {
        orderService.confirmCancellation(event);
    }

    // 배송 취소 거절 응답으로 주문을 CONFIRMED 상태로 되돌린다.
    @EventListener
    public void onDeliveryCancellationRejected(final DeliveryCancellationRejected event) {
        orderService.rejectCancellation(event);
    }

    // 기사 수락 사실을 주문의 배송 상태에 반영한다.
    @EventListener
    public void onDeliveryAccepted(final DeliveryAccepted event) {
        syncOrderDeliveryStatusService.sync(event);
    }

    // 기사 픽업 사실을 주문의 배송 상태에 반영한다.
    @EventListener
    public void onDeliveryPickedUp(final DeliveryPickedUp event) {
        syncOrderDeliveryStatusService.sync(event);
    }

    // 배송 완료 사실을 주문의 배송 상태에 반영한다.
    @EventListener
    public void onDeliveryDelivered(final DeliveryDelivered event) {
        syncOrderDeliveryStatusService.sync(event);
    }
}
