package setty.notification.send;

public record NotificationMessage(
        Long notificationId,
        Long memberId,
        Long listingId,
        String title,
        Priority priority
) {

    public enum Priority {
        HIGH,
        NORMAL
    }
}
