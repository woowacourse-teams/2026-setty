package setty.platform.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import setty.common.PaymentFailed;

@Component
@RequiredArgsConstructor
public class PaymentFailedListener {

    private final OrderService orderService;

    @EventListener
    public void handle(final PaymentFailed event) {
        orderService.cancelPending(event.orderId());
    }
}
