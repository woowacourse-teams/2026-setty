package setty.platform;

import setty.deliveryfee.VolumeTier;

/** 알림 모듈에 제공하는 매물 스냅샷. 배송비는 받는 사람 위치에 따라 달라지므로 계산에 필요한 값만 준다. */
public record NotifiedListing(
        Long id,
        String title,
        Integer price,
        String dongCode,
        Integer zipZone,
        VolumeTier volumeTier,
        Integer fixedDeliveryFee
) {
}
