package setty.notification.listing.presentation;

import java.time.Instant;
import setty.notification.listing.domain.ListingNotification;

public record NotificationResponse(
        Long id,
        String type,
        Long listingId,
        String title,
        Integer price,
        Integer deliveryFee,
        String listingStatus,
        String status,
        boolean read,
        Instant createdAt
) {

    static final String KEYWORD = "KEYWORD";

    public static NotificationResponse of(
            final ListingNotification notification,
            final String title,
            final Integer price,
            final Integer deliveryFee,
            final String listingStatus
    ) {
        return new NotificationResponse(
                notification.getId(),
                KEYWORD,
                notification.getListingId(),
                title,
                price,
                deliveryFee,
                listingStatus,
                notification.getStatus().name(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}
