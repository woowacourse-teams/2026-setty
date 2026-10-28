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

    public record Dong(String code, String name, String district) {
    }

    // 매물에 걸린 주문의 단계. NONE은 주문 없음, PAYING은 결제 대기 주문이 선점 중, TRADING은 결제가 끝나 배송 완료까지 선점 유지.
    public enum OrderState {
        NONE,
        PAYING,
        TRADING
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
            boolean hasPurchaseRequest,
            OrderState orderState,
            int favoriteCount,
            Instant createdAt,
            Dong dong
    ) {
    }

    public record SummaryPage(List<Summary> items, String nextCursor) {
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
            boolean hasPurchaseRequest,
            boolean purchaseRequestedByMe,
            OrderState orderState,
            Long myOrderId,
            int favoriteCount,
            List<Image> images,
            Instant createdAt,
            Instant updatedAt,
            Dong dong
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
            Instant createdAt,
            Dong dong
    ) {
    }

    public record PurchaseInfo(
            Long listingId,
            Long sellerId,
            String title,
            ListingCategory category,
            Integer price,
            Integer deliveryFee,
            Integer totalPrice,
            String dongCode,
            String zipCode
    ) {
    }

    public record Created(Long listingId, Instant createdAt) {
    }
}
