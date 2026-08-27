package setty.platform.order.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import setty.delivery.application.event.DeliveryStatusChanged;

@Component
public class DeliveryStatusChangedListener {

    private final SyncOrderDeliveryStatusService syncOrderDeliveryStatusService;

    public DeliveryStatusChangedListener(final SyncOrderDeliveryStatusService syncOrderDeliveryStatusService) {
        this.syncOrderDeliveryStatusService = syncOrderDeliveryStatusService;
    }

    @EventListener
    public void on(final DeliveryStatusChanged event) {
        syncOrderDeliveryStatusService.sync(event);
    }
}
