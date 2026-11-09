package setty.notification.send;

// 키워드 알림은 order가 null이다. 주문 알림은 title을 비워 옛 안드로이드 앱이 "새 매물"로 띄우지 않게 한다.
public record NotificationMessage(
        Long notificationId,
        Long memberId,
        Long listingId,
        String title,
        Integer price,
        Integer deliveryFee,
        Priority priority,
        Order order
) {

    public NotificationMessage(
            final Long notificationId,
            final Long memberId,
            final Long listingId,
            final String title,
            final Integer price,
            final Integer deliveryFee,
            final Priority priority
    ) {
        this(notificationId, memberId, listingId, title, price, deliveryFee, priority, null);
    }

    public static NotificationMessage order(
            final Long notificationId,
            final Long memberId,
            final Long listingId,
            final Order order
    ) {
        return new NotificationMessage(notificationId, memberId, listingId, null, null, null, Priority.HIGH, order);
    }

    public enum Priority {
        HIGH,
        NORMAL
    }

    public record Order(Long orderId, String kind, String recipient, String headline, String body) {
    }
}
