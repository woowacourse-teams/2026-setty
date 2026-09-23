package setty.platform.listing.application;

import java.time.Instant;
import java.util.List;
import setty.platform.listing.domain.ConditionGrade;
import setty.platform.listing.domain.ListingCategory;
import setty.platform.listing.domain.SaleStatus;

public final class ListingView {

    private ListingView() {
    }

    public record Dimensions(Integer widthCm, Integer depthCm, Integer heightCm) {
    }

    public record Image(Long id, String url, Integer displayOrder) {
    }

    public record Summary(
            Long id,
            String title,
            String thumbnailUrl,
            Integer price,
            Integer deliveryFee,
            Integer totalPrice,
            ListingCategory category,
            ConditionGrade conditionGrade,
            Dimensions dimensions,
            Instant createdAt
    ) {
    }

    public record Detail(
            Long id,
            String title,
            String description,
            Integer price,
            Integer deliveryFee,
            Integer totalPrice,
            ListingCategory category,
            ConditionGrade conditionGrade,
            Dimensions dimensions,
            SaleStatus saleStatus,
            List<Image> images,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record Mine(
            Long id,
            String title,
            String thumbnailUrl,
            Integer price,
            Integer deliveryFee,
            Integer totalPrice,
            ListingCategory category,
            ConditionGrade conditionGrade,
            Dimensions dimensions,
            SaleStatus saleStatus,
            boolean hasPurchaseRequest,
            boolean canUpdate,
            boolean canDelete,
            Instant createdAt
    ) {
    }

    public record PurchaseInfo(
            Long listingId,
            Long sellerId,
            String title,
            ListingCategory category,
            Integer price,
            Integer deliveryFee,
            Integer totalPrice
    ) {
    }

    public record Created(Long listingId, Instant createdAt) {
    }
}
