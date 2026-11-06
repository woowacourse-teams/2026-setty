package setty.notification.order.application;

import setty.notification.order.domain.OrderNotificationKind;
import setty.notification.order.domain.OrderNotificationRecipient;

public record PendingOrderNotification(
        Long id,
        Long memberId,
        OrderNotificationRecipient recipient,
        OrderNotificationKind kind
) {
}
