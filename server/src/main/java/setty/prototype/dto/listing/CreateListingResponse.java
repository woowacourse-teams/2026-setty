package setty.prototype.dto.listing;

import java.time.OffsetDateTime;
import setty.common.time.SeoulDateTime;
import setty.prototype.domain.Listing;

public record CreateListingResponse(
        Long listingId,
        OffsetDateTime createdAt
) {
    public static CreateListingResponse from(final Listing listing) {
        return new CreateListingResponse(
                listing.getId(),
                SeoulDateTime.toOffsetDateTime(listing.getCreatedAt())
        );
    }
}
