package setty.notification.send;

public record NotificationMessage(
        Long notificationId,
        Long memberId,
        Long listingId,
        String title,
        Integer price,
        Integer deliveryFee,
        Priority priority
) {

    public enum Priority {
        HIGH,
        NORMAL
    }
}
