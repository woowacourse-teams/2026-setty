package setty.notification.send;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import setty.notification.device.application.DeviceTokenService;
import setty.notification.send.fcm.FcmClient;
import setty.notification.send.fcm.FcmNotificationSender;
import tools.jackson.databind.ObjectMapper;

@Configuration
@ConditionalOnProperty(name = "setty.notification.sender", havingValue = "mixed")
public class MixedSendConfiguration {

    @Bean
    public MixedNotificationSender mixedNotificationSender(
            final DeviceTokenService deviceTokenService,
            final FcmClient fcmClient,
            final ObjectMapper objectMapper,
            final NotificationSendProperties properties
    ) {
        return new MixedNotificationSender(
                deviceTokenService,
                new FcmNotificationSender(deviceTokenService, fcmClient),
                new MockPushNotificationSender(objectMapper, properties));
    }
}
