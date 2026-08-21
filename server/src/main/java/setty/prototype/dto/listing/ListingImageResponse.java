package setty.prototype.dto.listing;

import setty.prototype.domain.ListingImage;

public record ListingImageResponse(
        Long id,
        String url,
        int displayOrder
) {
    public static ListingImageResponse from(final ListingImage image) {
        return new ListingImageResponse(image.getId(), image.getUrl(), image.getDisplayOrder());
    }
}
