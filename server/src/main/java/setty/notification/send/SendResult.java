package setty.notification.send;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record SendResult(Set<Long> failedNotificationIds) {

    public static SendResult allSucceeded() {
        return new SendResult(Set.of());
    }

    public static SendResult allFailed(final List<NotificationMessage> messages) {
        return new SendResult(messages.stream()
                .map(NotificationMessage::notificationId)
                .collect(Collectors.toSet()));
    }

    public boolean failed(final Long notificationId) {
        return failedNotificationIds.contains(notificationId);
    }
}
