package setty.prototype.dto.listing;

import java.time.OffsetDateTime;
import setty.common.time.SeoulDateTime;
import setty.prototype.domain.Listing;

public record ListingSummaryResponse(
        Long id,
        String title,
        Integer price,
        String thumbnailUrl,
        String pickupTimeText,
        boolean canHelpMove,
        OffsetDateTime createdAt
) {
    public static ListingSummaryResponse from(final Listing listing) {
        return new ListingSummaryResponse(
                listing.getId(),
                listing.getTitle(),
                listing.getPrice(),
                listing.thumbnailUrl(),
                listing.getPickupTimeText(),
                listing.isCanHelpMove(),
                SeoulDateTime.toOffsetDateTime(listing.getCreatedAt())
        );
    }
}
