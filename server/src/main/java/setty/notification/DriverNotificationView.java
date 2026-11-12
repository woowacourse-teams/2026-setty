package setty.notification;

import java.time.Instant;

public record DriverNotificationView(
        Long id,
        String kind,
        String headline,
        Integer amount,
        Long orderId,
        boolean read,
        Instant createdAt
) {
}
