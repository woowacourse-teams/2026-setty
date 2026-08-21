package setty.prototype.dto.listing;

import java.util.List;

public record ListingListResponse(
        List<ListingSummaryResponse> items
) {
}
