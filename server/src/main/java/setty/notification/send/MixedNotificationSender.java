package setty.notification.send;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import setty.notification.device.application.DeviceTokenService;
import setty.notification.device.domain.DeviceToken;
import setty.notification.owner.NotificationOwner;

public class MixedNotificationSender implements NotificationSender {

    private final DeviceTokenService deviceTokenService;
    private final NotificationSender deviceSender;
    private final NotificationSender loadSender;

    public MixedNotificationSender(
            final DeviceTokenService deviceTokenService,
            final NotificationSender deviceSender,
            final NotificationSender loadSender
    ) {
        this.deviceTokenService = deviceTokenService;
        this.deviceSender = deviceSender;
        this.loadSender = loadSender;
    }

    @Override
    public SendResult send(final List<NotificationMessage> messages) {
        final Set<NotificationOwner> ownersWithDevice = ownersWithDevice(messages);
        final Map<Boolean, List<NotificationMessage>> byDevice = messages.stream()
                .collect(Collectors.partitioningBy(message -> ownersWithDevice.contains(message.owner())));
        final Set<Long> failed = new HashSet<>();
        failed.addAll(sendIfAny(loadSender, byDevice.get(false)));
        failed.addAll(sendIfAny(deviceSender, byDevice.get(true)));
        return new SendResult(failed);
    }

    private Set<NotificationOwner> ownersWithDevice(final List<NotificationMessage> messages) {
        final Set<NotificationOwner> owners = messages.stream().map(NotificationMessage::owner).collect(Collectors.toSet());
        return deviceTokenService.findAllByOwners(owners).stream()
                .map(DeviceToken::getOwner)
                .collect(Collectors.toSet());
    }

    private Set<Long> sendIfAny(final NotificationSender sender, final List<NotificationMessage> messages) {
        if (messages.isEmpty()) {
            return Set.of();
        }
        return sender.send(messages).failedNotificationIds();
    }
}
