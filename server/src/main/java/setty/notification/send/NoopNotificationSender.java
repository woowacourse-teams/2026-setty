package setty.notification.send;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "setty.notification.sender", havingValue = "noop", matchIfMissing = true)
public class NoopNotificationSender implements NotificationSender {

    @Override
    public SendResult send(final List<NotificationMessage> messages) {
        return SendResult.allSucceeded();
    }
}
