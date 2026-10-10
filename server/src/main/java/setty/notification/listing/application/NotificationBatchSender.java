package setty.notification.listing.application;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import setty.notification.listing.domain.ListingNotification;
import setty.notification.send.NotificationMessage;
import setty.notification.send.NotificationSender;
import setty.notification.send.SendResult;

@Component
public class NotificationBatchSender {

    private static final Logger log = LoggerFactory.getLogger(NotificationBatchSender.class);

    private final NotificationSender notificationSender;
    private final ListingNotificationService listingNotificationService;

    public NotificationBatchSender(
            final NotificationSender notificationSender,
            final ListingNotificationService listingNotificationService
    ) {
        this.notificationSender = notificationSender;
        this.listingNotificationService = listingNotificationService;
    }

    public void send(final List<ListingNotification> batch, final String title) {
        final List<NotificationMessage> messages = batch.stream()
                .map(notification -> toMessage(notification, title))
                .toList();
        final SendResult result = sendSafely(messages);
        final List<Long> sent = new ArrayList<>();
        final List<Long> failed = new ArrayList<>();
        for (final ListingNotification notification : batch) {
            (result.failed(notification.getId()) ? failed : sent).add(notification.getId());
        }
        listingNotificationService.markSent(sent);
        listingNotificationService.markFailedAttempt(failed);
    }

    private SendResult sendSafely(final List<NotificationMessage> messages) {
        try {
            return notificationSender.send(messages);
        } catch (final RuntimeException sendFailed) {
            log.warn("notification batch send failed size={} cause={}", messages.size(), sendFailed.toString());
            return SendResult.allFailed(messages);
        }
    }

    private NotificationMessage toMessage(final ListingNotification notification, final String title) {
        return new NotificationMessage(
                notification.getId(),
                notification.getMemberId(),
                notification.getListingId(),
                title,
                NotificationMessage.Priority.HIGH
        );
    }
}
