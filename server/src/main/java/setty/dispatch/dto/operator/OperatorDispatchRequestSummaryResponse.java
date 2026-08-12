package setty.dispatch.dto.operator;

import java.time.OffsetDateTime;
import setty.common.time.SeoulDateTime;
import setty.dispatch.domain.DispatchRequest;
import setty.dispatch.domain.DispatchStatus;

public record OperatorDispatchRequestSummaryResponse(
        Long id,
        DispatchStatus status,
        String itemType,
        boolean highValueItem,
        boolean sellerInputCompleted,
        Integer finalQuotedAmount,
        OffsetDateTime createdAt
) {
    public static OperatorDispatchRequestSummaryResponse from(final DispatchRequest dispatchRequest) {
        return new OperatorDispatchRequestSummaryResponse(
                dispatchRequest.getId(),
                dispatchRequest.getStatus(),
                dispatchRequest.getItemType(),
                dispatchRequest.isHighValueItem(),
                dispatchRequest.isSellerInputCompleted(),
                dispatchRequest.getFinalQuotedAmount(),
                SeoulDateTime.toOffsetDateTime(dispatchRequest.getCreatedAt())
        );
    }
}
