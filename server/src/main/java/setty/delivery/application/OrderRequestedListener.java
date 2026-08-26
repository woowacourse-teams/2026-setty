package setty.delivery.application;

import java.time.Instant;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import setty.common.OrderRequested;

@Component
public class OrderRequestedListener {

    private final RegisterDeliveryService registerDeliveryService;

    public OrderRequestedListener(final RegisterDeliveryService registerDeliveryService) {
        this.registerDeliveryService = registerDeliveryService;
    }

    @EventListener
    public void on(final OrderRequested event) {
        registerDeliveryService.register(event, Instant.now());
    }
}
