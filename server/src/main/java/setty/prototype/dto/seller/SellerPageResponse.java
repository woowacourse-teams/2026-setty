package setty.prototype.dto.seller;

import java.time.OffsetDateTime;
import java.util.List;

public record SellerPageResponse(
        Seller seller,
        Summary summary,
        List<SellerListing> listings
) {
    public record Seller(
            String phoneNumber
    ) {
    }

    public record Summary(
            int listingCount,
            long messageCount
    ) {
    }

    public record SellerListing(
            Long id,
            String title,
            String thumbnailUrl,
            String pickupTimeText,
            boolean canHelpMove,
            long messageCount,
            OffsetDateTime latestMessageAt,
            OffsetDateTime createdAt
    ) {
    }
}
