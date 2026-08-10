package setty.estimate.application.query;

import java.time.OffsetDateTime;
import setty.estimate.domain.EstimateRequestStatus;

public record EstimateRequestDetail(
        Long estimateRequestId,
        String name,
        String phoneNumber,
        String tradeArea,
        String itemType,
        boolean highValueItem,
        String productLink,
        EstimateRequestStatus status,
        OffsetDateTime createdAt,
        ManualNotificationResult manualNotification
) {
}
