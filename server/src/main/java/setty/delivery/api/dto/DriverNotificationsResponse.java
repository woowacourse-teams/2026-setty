package setty.delivery.api.dto;

import java.util.List;
import setty.notification.DriverNotificationView;

public record DriverNotificationsResponse(List<DriverNotificationView> items) {
}
