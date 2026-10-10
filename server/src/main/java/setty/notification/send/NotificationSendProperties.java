package setty.notification.send;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "setty.notification")
public record NotificationSendProperties(int sendConcurrency, boolean syncSend) {

    private static final int DEFAULT_SEND_CONCURRENCY = 8;

    public NotificationSendProperties {
        if (sendConcurrency <= 0) {
            sendConcurrency = DEFAULT_SEND_CONCURRENCY;
        }
    }
}
