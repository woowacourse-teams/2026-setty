package setty.common;

/** 주문이 확정되었을 때 배송 영역에 전달하는 주문 시점의 배송 정보와 정산 영역에 전달하는 판매자 정산 정보. */
public record OrderConfirmed(
        Long orderId,
        String itemName,
        String category,
        String pickupAddress,
        String deliveryAddress,
        int deliveryFee,
        String pickupPhoneNumber,
        String deliveryPhoneNumber,
        Long listingId,
        Long sellerId,
        int itemPrice
) {
}
