package setty.notification.listing.application;

import java.util.List;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import setty.common.ListingCreated;
import setty.notification.listing.domain.ListingNotification;
import setty.notification.send.NotificationSendProperties;

@Component
public class ListingNotificationCreationListener {

    private final ListingNotificationService listingNotificationService;
    private final NotificationBatchSender batchSender;
    private final NotificationSendProperties properties;

    public ListingNotificationCreationListener(
            final ListingNotificationService listingNotificationService,
            final NotificationBatchSender batchSender,
            final NotificationSendProperties properties
    ) {
        this.listingNotificationService = listingNotificationService;
        this.batchSender = batchSender;
        this.properties = properties;
    }

    @EventListener
    public void onListingCreated(final ListingCreated event) {
        listingNotificationService.createForListing(event.listingId(), event.sellerId(), event.title());
        if (properties.syncSend()) {
            sendOneByOneInsideTransaction(event);
        }
    }

    private void sendOneByOneInsideTransaction(final ListingCreated event) {
        long afterId = 0L;
        while (true) {
            final List<ListingNotification> batch = listingNotificationService.findPendingBatch(event.listingId(), afterId);
            if (batch.isEmpty()) {
                return;
            }
            for (final ListingNotification notification : batch) {
                batchSender.send(List.of(notification), event.title());
            }
            afterId = batch.getLast().getId();
        }
    }
}
