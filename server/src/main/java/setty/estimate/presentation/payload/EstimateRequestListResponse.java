package setty.estimate.presentation.payload;

import java.time.OffsetDateTime;
import setty.estimate.application.query.EstimateRequestSummary;
import setty.estimate.domain.EstimateRequestStatus;

public record EstimateRequestListResponse(
        Long estimateRequestId,
        String tradeArea,
        String itemType,
        boolean highValueItem,
        EstimateRequestStatus status,
        OffsetDateTime createdAt
) {
    public static EstimateRequestListResponse from(final EstimateRequestSummary estimateRequestSummary) {
        return new EstimateRequestListResponse(
                estimateRequestSummary.estimateRequestId(),
                estimateRequestSummary.tradeArea(),
                estimateRequestSummary.itemType(),
                estimateRequestSummary.highValueItem(),
                estimateRequestSummary.status(),
                estimateRequestSummary.createdAt()
        );
    }
}
