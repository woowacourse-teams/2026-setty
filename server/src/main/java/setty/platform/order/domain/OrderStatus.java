package setty.platform.order.domain;

public enum OrderStatus {
    PENDING,
    // 기한 안의 승인을 주문이 받아들였고 확정(배송 요청)만 남은 상태. 만료·취소 대상이 아니다.
    PAID,
    CONFIRMED,
    COMPLETED,
    CANCEL_PENDING,
    CANCELLED,
    EXPIRED
}
