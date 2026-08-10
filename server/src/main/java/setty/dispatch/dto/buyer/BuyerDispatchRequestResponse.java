package setty.dispatch.dto.buyer;

import java.time.OffsetDateTime;
import java.util.List;
import setty.common.phone.PhoneNumbers;
import setty.common.time.SeoulDateTime;
import setty.dispatch.domain.DispatchRequest;
import setty.dispatch.domain.DispatchStatus;

public record BuyerDispatchRequestResponse(
        DispatchStatus status,
        String buyerName,
        String buyerPhoneNumber,
        String deliveryAddress,
        String itemType,
        boolean highValueItem,
        String productLink,
        List<String> itemImageUrls,
        boolean sellerInputCompleted,
        OffsetDateTime createdAt,
        String sellerInputUrl,
        Integer finalQuotedAmount
) {
    public static BuyerDispatchRequestResponse from(
            final DispatchRequest dispatchRequest,
            final String sellerInputUrl
    ) {
        return new BuyerDispatchRequestResponse(
                dispatchRequest.getStatus(),
                dispatchRequest.getBuyerName(),
                PhoneNumbers.format(dispatchRequest.getBuyerPhoneNumber()),
                dispatchRequest.getDeliveryAddress(),
                dispatchRequest.getItemType(),
                dispatchRequest.isHighValueItem(),
                dispatchRequest.getProductLink(),
                dispatchRequest.getItemImageUrls(),
                dispatchRequest.isSellerInputCompleted(),
                SeoulDateTime.toOffsetDateTime(dispatchRequest.getCreatedAt()),
                sellerInputUrl,
                dispatchRequest.getFinalQuotedAmount()
        );
    }
}
