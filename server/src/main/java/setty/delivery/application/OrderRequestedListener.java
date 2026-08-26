package setty.delivery.application;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import setty.common.OrderRequested;

@Component
@RequiredArgsConstructor
public class OrderRequestedListener {

    private final RegisterDeliveryService registerDeliveryService;

    @EventListener
    public void on(final OrderRequested event) {
        registerDeliveryService.register(event, Instant.now());
    }
}
