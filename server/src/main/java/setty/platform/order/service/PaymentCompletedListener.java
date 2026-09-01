package setty.platform.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import setty.common.PaymentCompleted;

@Component
@RequiredArgsConstructor
public class PaymentCompletedListener {

    private final OrderService orderService;

    @EventListener
    public void handle(final PaymentCompleted event) {
        orderService.publishOrderRequested(event.orderId());
    }
}
