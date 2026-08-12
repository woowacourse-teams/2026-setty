package setty.dispatch.dto.buyer;

public record BuyerDispatchRequestCreateResponse(
        String buyerToken,
        String sellerInputUrl
) {
}
