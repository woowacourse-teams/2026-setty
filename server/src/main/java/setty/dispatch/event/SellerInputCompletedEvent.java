package setty.dispatch.event;

import java.time.LocalDateTime;
import setty.dispatch.domain.DispatchRequest;

/**
 * 판매자 입력이 끝나 운영자가 검토할 수 있게 된 시점의 사건이다.
 * 운영자 알림에 필요한 값만 담는다. 구매자·판매자의 이름·연락처·주소와 물품 사진 URL은
 * 외부 채널로 나가지 않도록 담지 않는다(DEC-017).
 */
public record SellerInputCompletedEvent(
        Long dispatchRequestId,
        String itemType,
        boolean highValueItem,
        int itemImageCount,
        String productLink,
        LocalDateTime createdAt,
        LocalDateTime sellerInputCompletedAt
) {
    public static SellerInputCompletedEvent from(final DispatchRequest dispatchRequest) {
        return new SellerInputCompletedEvent(
                dispatchRequest.getId(),
                dispatchRequest.getItemType(),
                dispatchRequest.isHighValueItem(),
                dispatchRequest.getItemImageUrls().size(),
                dispatchRequest.getProductLink(),
                dispatchRequest.getCreatedAt(),
                dispatchRequest.getSellerInputCompletedAt()
        );
    }
}
