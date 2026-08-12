package setty.dispatch.dto.operator;

import java.time.OffsetDateTime;
import java.util.List;
import setty.common.phone.PhoneNumbers;
import setty.common.time.SeoulDateTime;
import setty.dispatch.domain.DispatchRequest;
import setty.dispatch.domain.DispatchStatus;
import setty.dispatch.domain.SellerInput;

public record OperatorDispatchRequestDetailResponse(
        Long id,
        DispatchStatus status,
        String itemType,
        boolean highValueItem,
        String productLink,
        List<String> itemImageUrls,
        Long estimateRequestId,
        OffsetDateTime createdAt,
        Buyer buyer,
        Seller seller,
        String sellerInputUrl,
        OffsetDateTime sellerInputCompletedAt,
        Integer finalQuotedAmount,
        String messageContent,
        String buyerConfirmUrl,
        OffsetDateTime amountCheckedAt,
        OffsetDateTime buyerPrivacyConsentedAt,
        String buyerPrivacyPolicyVersion,
        OffsetDateTime sellerPrivacyConsentedAt,
        String sellerPrivacyPolicyVersion,
        String operatorNote,
        String closedReason
) {
    public record Buyer(
            String name,
            String phoneNumber,
            String deliveryAddress
    ) {
    }

    public record Seller(
            String name,
            String phoneNumber,
            String pickupAddress,
            String availablePickupTime
    ) {
    }

    public static OperatorDispatchRequestDetailResponse from(
            final DispatchRequest dispatchRequest,
            final String sellerInputUrl,
            final String buyerConfirmUrl
    ) {
        return new OperatorDispatchRequestDetailResponse(
                dispatchRequest.getId(),
                dispatchRequest.getStatus(),
                dispatchRequest.getItemType(),
                dispatchRequest.isHighValueItem(),
                dispatchRequest.getProductLink(),
                dispatchRequest.getItemImageUrls(),
                dispatchRequest.getEstimateRequestId(),
                SeoulDateTime.toOffsetDateTime(dispatchRequest.getCreatedAt()),
                new Buyer(
                        dispatchRequest.getBuyerName(),
                        PhoneNumbers.format(dispatchRequest.getBuyerPhoneNumber()),
                        dispatchRequest.getDeliveryAddress()
                ),
                toSeller(dispatchRequest.getSellerInput()),
                sellerInputUrl,
                SeoulDateTime.toOffsetDateTime(dispatchRequest.getSellerInputCompletedAt()),
                dispatchRequest.getFinalQuotedAmount(),
                dispatchRequest.getMessageContent(),
                buyerConfirmUrl,
                SeoulDateTime.toOffsetDateTime(dispatchRequest.getAmountCheckedAt()),
                SeoulDateTime.toOffsetDateTime(dispatchRequest.getBuyerPrivacyConsentedAt()),
                dispatchRequest.getBuyerPrivacyPolicyVersion(),
                SeoulDateTime.toOffsetDateTime(dispatchRequest.getSellerPrivacyConsentedAt()),
                dispatchRequest.getSellerPrivacyPolicyVersion(),
                dispatchRequest.getOperatorNote(),
                dispatchRequest.getClosedReason()
        );
    }

    private static Seller toSeller(final SellerInput sellerInput) {
        if (sellerInput == null || !sellerInput.isPresent()) {
            return null;
        }
        return new Seller(
                sellerInput.getSellerName(),
                PhoneNumbers.format(sellerInput.getSellerPhoneNumber()),
                sellerInput.getPickupAddress(),
                sellerInput.getAvailablePickupTime()
        );
    }
}
