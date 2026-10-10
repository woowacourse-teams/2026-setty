package setty.notification.listing.application;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import setty.common.ListingCreated;

@Component
public class ListingNotificationCreationListener {

    private final ListingNotificationService listingNotificationService;

    public ListingNotificationCreationListener(final ListingNotificationService listingNotificationService) {
        this.listingNotificationService = listingNotificationService;
    }

    @EventListener
    public void onListingCreated(final ListingCreated event) {
        listingNotificationService.createForListing(event.listingId(), event.sellerId(), event.title());
    }
}
