package setty.notification.send.fcm;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import setty.notification.device.application.DeviceTokenService;
import setty.notification.device.domain.DeviceToken;
import setty.notification.send.NotificationMessage;
import setty.notification.send.NotificationSender;
import setty.notification.send.SendResult;

@Component
@ConditionalOnProperty(name = "setty.notification.sender", havingValue = "fcm")
public class FcmNotificationSender implements NotificationSender {

    private static final String TITLE = "새 매물이 올라왔어요";

    private final DeviceTokenService deviceTokenService;
    private final FcmClient fcmClient;

    public FcmNotificationSender(final DeviceTokenService deviceTokenService, final FcmClient fcmClient) {
        this.deviceTokenService = deviceTokenService;
        this.fcmClient = fcmClient;
    }

    @Override
    public SendResult send(final List<NotificationMessage> messages) {
        final Map<Long, List<String>> tokensByMember = tokensByMember(messages);
        final List<Message> fcmMessages = new ArrayList<>();
        final List<Target> targets = new ArrayList<>();
        for (final NotificationMessage message : messages) {
            for (final String token : tokensByMember.getOrDefault(message.memberId(), List.of())) {
                fcmMessages.add(toFcmMessage(message, token));
                targets.add(new Target(message.notificationId(), token));
            }
        }
        return collect(messages, targets, deliverInBatches(fcmMessages));
    }

    private Map<Long, List<String>> tokensByMember(final List<NotificationMessage> messages) {
        final Set<Long> memberIds = new HashSet<>();
        for (final NotificationMessage message : messages) {
            memberIds.add(message.memberId());
        }
        final Map<Long, List<String>> result = new HashMap<>();
        for (final DeviceToken deviceToken : deviceTokenService.findAllByMemberIds(memberIds)) {
            result.computeIfAbsent(deviceToken.getMemberId(), ignored -> new ArrayList<>()).add(deviceToken.getToken());
        }
        return result;
    }

    private List<FcmDelivery> deliverInBatches(final List<Message> fcmMessages) {
        final List<FcmDelivery> deliveries = new ArrayList<>(fcmMessages.size());
        for (int from = 0; from < fcmMessages.size(); from += MAXIMUM_BATCH_SIZE) {
            final int to = Math.min(from + MAXIMUM_BATCH_SIZE, fcmMessages.size());
            deliveries.addAll(fcmClient.sendEach(fcmMessages.subList(from, to)));
        }
        return deliveries;
    }

    private SendResult collect(
            final List<NotificationMessage> messages,
            final List<Target> targets,
            final List<FcmDelivery> deliveries
    ) {
        final Set<Long> deliveredNotifications = new HashSet<>();
        final Set<Long> attemptedNotifications = new HashSet<>();
        final Set<String> invalidTokens = new HashSet<>();
        for (int i = 0; i < targets.size(); i++) {
            final Target target = targets.get(i);
            final FcmDelivery delivery = deliveries.get(i);
            attemptedNotifications.add(target.notificationId());
            if (delivery.delivered()) {
                deliveredNotifications.add(target.notificationId());
            } else if (delivery.tokenInvalid()) {
                invalidTokens.add(target.token());
            }
        }
        deviceTokenService.removeInvalid(invalidTokens);

        final Set<Long> failed = new HashSet<>();
        for (final NotificationMessage message : messages) {
            final Long id = message.notificationId();
            if (attemptedNotifications.contains(id) && !deliveredNotifications.contains(id)) {
                failed.add(id);
            }
        }
        return new SendResult(failed);
    }

    private Message toFcmMessage(final NotificationMessage message, final String token) {
        return Message.builder()
                .setToken(token)
                .setNotification(Notification.builder().setTitle(TITLE).setBody(message.title()).build())
                .putData("notificationId", String.valueOf(message.notificationId()))
                .putData("listingId", String.valueOf(message.listingId()))
                .putData("title", message.title())
                .setAndroidConfig(AndroidConfig.builder().setPriority(toAndroidPriority(message)).build())
                .build();
    }

    private AndroidConfig.Priority toAndroidPriority(final NotificationMessage message) {
        return message.priority() == NotificationMessage.Priority.HIGH
                ? AndroidConfig.Priority.HIGH
                : AndroidConfig.Priority.NORMAL;
    }

    private record Target(Long notificationId, String token) {
    }
}
