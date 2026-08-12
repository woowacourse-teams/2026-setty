package setty.estimate.application.event;

import java.time.LocalDateTime;
import setty.estimate.domain.EstimateRequest;

/**
 * 운영자 알림에 필요한 값만 담는다.
 * 신청자 이름·연락처·거래 지역은 외부 채널로 나가지 않도록 담지 않는다(DEC-017, DEC-020).
 */
public record EstimateRequestCreatedEvent(
        Long estimateRequestId,
        String itemType,
        boolean highValueItem,
        String productLink,
        LocalDateTime createdAt
) {
    public static EstimateRequestCreatedEvent from(final EstimateRequest estimateRequest) {
        return new EstimateRequestCreatedEvent(
                estimateRequest.getId(),
                estimateRequest.getItemType(),
                estimateRequest.isHighValueItem(),
                estimateRequest.getProductLink(),
                estimateRequest.getCreatedAt()
        );
    }
}
