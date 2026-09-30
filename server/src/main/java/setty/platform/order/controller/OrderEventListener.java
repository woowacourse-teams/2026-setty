package setty.platform.order.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import setty.common.DeliveryAccepted;
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

    @EventListener
    public void onPaymentCompleted(final PaymentCompleted event) {
        orderService.publishOrderConfirmed(event.orderId());
    }

    @EventListener
    public void onPaymentFailed(final PaymentFailed event) {
        orderService.cancelPending(event.orderId());
    }

    @EventListener
    public void onDeliveryAccepted(final DeliveryAccepted event) {
        syncOrderDeliveryStatusService.sync(event);
    }

    @EventListener
    public void onDeliveryPickedUp(final DeliveryPickedUp event) {
        syncOrderDeliveryStatusService.sync(event);
    }

    @EventListener
    public void onDeliveryDelivered(final DeliveryDelivered event) {
        syncOrderDeliveryStatusService.sync(event);
    }
}
