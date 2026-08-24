package setty.prototype.dto.listing;

import java.time.OffsetDateTime;
import java.util.List;
import setty.common.time.SeoulDateTime;
import setty.prototype.domain.Listing;

/**
 * 구매자에게도 열려 있는 응답이라 판매자 휴대폰 번호와 비밀번호를 담지 않는다.
 */
public record ListingDetailResponse(
        Long id,
        String title,
        Integer price,
        String description,
        String pickupTimeText,
        boolean canHelpMove,
        List<ListingImageResponse> images,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ListingDetailResponse from(final Listing listing) {
        return new ListingDetailResponse(
                listing.getId(),
                listing.getTitle(),
                listing.getPrice(),
                listing.getDescription(),
                listing.getPickupTimeText(),
                listing.isCanHelpMove(),
                listing.getImages().stream()
                        .map(ListingImageResponse::from)
                        .toList(),
                SeoulDateTime.toOffsetDateTime(listing.getCreatedAt()),
                SeoulDateTime.toOffsetDateTime(listing.getUpdatedAt())
        );
    }
}
