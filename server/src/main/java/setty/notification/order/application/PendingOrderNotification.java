package setty.notification.order.application;

import setty.notification.order.domain.OrderNotificationKind;
import setty.notification.order.domain.OrderNotificationRecipient;
import setty.notification.owner.NotificationOwner;

public record PendingOrderNotification(
        Long id,
        NotificationOwner owner,
        OrderNotificationRecipient recipient,
        OrderNotificationKind kind
) {
}
