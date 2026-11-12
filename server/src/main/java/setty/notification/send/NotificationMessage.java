package setty.notification.send;

import setty.notification.owner.NotificationOwner;

// 키워드 알림은 order가 null이다. 주문 알림은 title을 비워 옛 안드로이드 앱이 "새 매물"로 띄우지 않게 한다.
// 기사 알림은 매물이 없어 listingId가 null일 수 있다.
public record NotificationMessage(
        Long notificationId,
        NotificationOwner owner,
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
        this(notificationId, NotificationOwner.member(memberId), listingId, title, price, deliveryFee, priority, null);
    }

    public static NotificationMessage order(
            final Long notificationId,
            final NotificationOwner owner,
            final Long listingId,
            final Order order
    ) {
        return new NotificationMessage(notificationId, owner, listingId, null, null, null, Priority.HIGH, order);
    }

    public enum Priority {
        HIGH,
        NORMAL
    }

    public record Order(Long orderId, String kind, String recipient, String headline, String body) {
    }
}
