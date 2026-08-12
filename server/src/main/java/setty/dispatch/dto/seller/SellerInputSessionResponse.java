package setty.dispatch.dto.seller;

import setty.dispatch.domain.SellerInputSession;

public record SellerInputSessionResponse(
        String itemType,
        boolean alreadySubmitted
) {
    public static SellerInputSessionResponse from(final SellerInputSession session) {
        return new SellerInputSessionResponse(
                session.getDispatchRequest().getItemType(),
                session.isCompleted()
        );
    }
}
