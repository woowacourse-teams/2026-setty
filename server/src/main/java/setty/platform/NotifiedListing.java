package setty.platform;

import setty.deliveryfee.VolumeTier;

/**
 * 알림 모듈에 제공하는 매물 스냅샷. 배송비는 받는 사람 위치에 따라 달라지므로 계산에 필요한 값만 준다.
 * tradeStatus는 AVAILABLE·TRADING·SOLD·DELETED 중 하나다. 결제 대기 주문이 있거나 배차된 매물은 살 수 없으므로 TRADING으로 묶는다.
 */
public record NotifiedListing(
        Long id,
        String title,
        Integer price,
        String dongCode,
        Integer zipZone,
        VolumeTier volumeTier,
        Integer fixedDeliveryFee,
        String tradeStatus
) {
}
