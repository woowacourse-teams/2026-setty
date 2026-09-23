package setty.common;

public record OrderRequested(
        Long orderId,
        String itemName,
        String category,
        String pickupAddress,
        String deliveryAddress,
        int deliveryFee,
        String pickupPhoneNumber,
        String deliveryPhoneNumber
) {
}
