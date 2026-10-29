package setty.notification.listing.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import setty.notification.listing.domain.ListingNotification;
import setty.notification.send.NotificationMessage;
import setty.notification.send.NotificationSender;
import setty.notification.send.SendResult;
import setty.platform.MemberDeliveryArea;
import setty.platform.NotificationMemberReader;

@Component
public class NotificationBatchSender {

    private static final Logger log = LoggerFactory.getLogger(NotificationBatchSender.class);

    private final NotificationSender notificationSender;
    private final ListingNotificationService listingNotificationService;
    private final NotificationMemberReader notificationMemberReader;
    private final NotificationDeliveryFee notificationDeliveryFee;

    public NotificationBatchSender(
            final NotificationSender notificationSender,
            final ListingNotificationService listingNotificationService,
            final NotificationMemberReader notificationMemberReader,
            final NotificationDeliveryFee notificationDeliveryFee
    ) {
        this.notificationSender = notificationSender;
        this.listingNotificationService = listingNotificationService;
        this.notificationMemberReader = notificationMemberReader;
        this.notificationDeliveryFee = notificationDeliveryFee;
    }

    public long send(final List<ListingNotification> batch, final ListingToNotify listing) {
        final Map<Long, MemberDeliveryArea> areas = deliveryAreasOf(batch);
        final List<NotificationMessage> messages = batch.stream()
                .map(notification -> toMessage(notification, listing, areas.get(notification.getMemberId())))
                .toList();
        final SendResult result = sendSafely(messages);
        final List<Long> sent = new ArrayList<>();
        final List<Long> failed = new ArrayList<>();
        for (final ListingNotification notification : batch) {
            (result.failed(notification.getId()) ? failed : sent).add(notification.getId());
        }
        listingNotificationService.markSent(sent);
        return listingNotificationService.markFailedAttempt(failed);
    }

    private Map<Long, MemberDeliveryArea> deliveryAreasOf(final List<ListingNotification> batch) {
        final Map<Long, MemberDeliveryArea> areas = new HashMap<>();
        for (final MemberDeliveryArea area : notificationMemberReader.findDeliveryAreas(
                batch.stream().map(ListingNotification::getMemberId).distinct().toList())) {
            areas.put(area.memberId(), area);
        }
        return areas;
    }

    private SendResult sendSafely(final List<NotificationMessage> messages) {
        try {
            return notificationSender.send(messages);
        } catch (final RuntimeException sendFailed) {
            log.warn("notification batch send failed size={} cause={}", messages.size(), sendFailed.toString());
            return SendResult.allFailed(messages);
        }
    }

    private NotificationMessage toMessage(
            final ListingNotification notification,
            final ListingToNotify listing,
            final MemberDeliveryArea receiver
    ) {
        return new NotificationMessage(
                notification.getId(),
                notification.getMemberId(),
                notification.getListingId(),
                listing.title(),
                listing.price(),
                notificationDeliveryFee.of(listing, receiver),
                NotificationMessage.Priority.HIGH
        );
    }
}
