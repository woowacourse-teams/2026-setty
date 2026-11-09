package setty.common;

/**
 * 주문이 확정되었을 때 배송 영역에 전달하는 주문 시점의 배송 정보와 정산 영역에 전달하는 판매자 정산 정보.
 * 출발·도착은 주문 때 저장한 건물 관리번호로도 전달한다. 이벤트가 발행 기록 테이블에 직렬화되어 남으므로 좌표는 싣지 않는다.
 */
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
        int itemPrice,
        String deliveryDongCode,
        String pickupDongCode,
        String pickupZipCode,
        String deliveryZipCode,
        String pickupAddressManagementNumber,
        String deliveryAddressManagementNumber
) {
}
