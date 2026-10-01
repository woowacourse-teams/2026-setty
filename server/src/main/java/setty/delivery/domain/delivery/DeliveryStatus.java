package setty.delivery.domain.delivery;

/** 배송 컨텍스트가 소유하는 배송 상태. 주문의 배송 상태(setty.common.DeliveryStatus)와는 별개다. */
public enum DeliveryStatus {

    REQUESTED,
    ACCEPTED,
    PICKED_UP,
    DELIVERED,
    CANCELLED
}
