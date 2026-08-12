package setty.estimate.presentation.payload;

import java.time.OffsetDateTime;
import setty.estimate.application.result.CreatedEstimateRequest;
import setty.estimate.domain.EstimateRequestStatus;

public record EstimateRequestCreateResponse(
        Long estimateRequestId,
        EstimateRequestStatus status,
        OffsetDateTime createdAt
) {
    public static EstimateRequestCreateResponse from(final CreatedEstimateRequest createdEstimateRequest) {
        return new EstimateRequestCreateResponse(
                createdEstimateRequest.estimateRequestId(),
                createdEstimateRequest.status(),
                createdEstimateRequest.createdAt()
        );
    }
}
