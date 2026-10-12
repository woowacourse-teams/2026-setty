package setty.notification.send;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "setty.notification")
public record NotificationSendProperties(
        int sendConcurrency,
        String mockPushUrl,
        String fcmCredentialsPath
) {

    private static final int DEFAULT_SEND_CONCURRENCY = 4;
    private static final String DEFAULT_MOCK_PUSH_URL = "http://127.0.0.1:9000";

    public NotificationSendProperties {
        if (sendConcurrency <= 0) {
            sendConcurrency = DEFAULT_SEND_CONCURRENCY;
        }
        if (mockPushUrl == null || mockPushUrl.isBlank()) {
            mockPushUrl = DEFAULT_MOCK_PUSH_URL;
        }
    }
}
