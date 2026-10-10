package setty.notification.send;

import java.util.List;

public interface NotificationSender {

    int MAXIMUM_BATCH_SIZE = 500;

    SendResult send(List<NotificationMessage> messages);
}
