package setty.dispatch.event;

import java.time.LocalDateTime;
import setty.dispatch.domain.DispatchRequest;

/**
 * 운영자 알림에 필요한 값만 담는다.
 * 구매자 이름·연락처·배송 상세주소와 물품 사진 URL은 외부 채널로 나가지 않도록 담지 않는다(DEC-017).
 */
public record DispatchRequestCreatedEvent(
        Long dispatchRequestId,
        String itemType,
        boolean highValueItem,
        int itemImageCount,
        String productLink,
        LocalDateTime createdAt
) {
    public static DispatchRequestCreatedEvent from(final DispatchRequest dispatchRequest) {
        return new DispatchRequestCreatedEvent(
                dispatchRequest.getId(),
                dispatchRequest.getItemType(),
                dispatchRequest.isHighValueItem(),
                dispatchRequest.getItemImageUrls().size(),
                dispatchRequest.getProductLink(),
                dispatchRequest.getCreatedAt()
        );
    }
}
