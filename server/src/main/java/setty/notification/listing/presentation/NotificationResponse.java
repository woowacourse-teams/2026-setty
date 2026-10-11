package setty.notification.listing.presentation;

import java.time.Instant;
import setty.notification.listing.domain.ListingNotification;

public record NotificationResponse(Long id, Long listingId, String title, String status, Instant createdAt) {

    public static NotificationResponse of(final ListingNotification notification, final String title) {
        return new NotificationResponse(
                notification.getId(),
                notification.getListingId(),
                title,
                notification.getStatus().name(),
                notification.getCreatedAt()
        );
    }
}
