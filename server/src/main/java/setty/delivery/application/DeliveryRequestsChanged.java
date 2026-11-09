package setty.delivery.application;

import setty.delivery.domain.delivery.Delivery;

/** 배송 요청 목록을 다시 조회해야 함을 나타내는 배송 모듈 내부 이벤트다. 칸 캐시는 바뀐 요청의 칸 쌍만 다시 읽는다. */
public record DeliveryRequestsChanged(Integer pickupCell, Integer deliveryCell) {

    public static DeliveryRequestsChanged of(final Delivery delivery) {
        return new DeliveryRequestsChanged(delivery.getRoute().pickupCell(), delivery.getRoute().deliveryCell());
    }
}
