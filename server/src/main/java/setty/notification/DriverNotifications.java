package setty.notification;

import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.notification.device.application.DeviceTokenService;
import setty.notification.device.domain.DevicePlatform;
import setty.notification.order.application.OrderNotificationService;
import setty.notification.order.domain.OrderNotification;
import setty.notification.owner.NotificationOwner;

// 기사 알림의 공개 창구. 기사 로그인은 배송 모듈이 풀고 여기에는 기사 ID만 온다.
@Service
public class DriverNotifications {

    private static final int MAXIMUM_PAGE_SIZE = 100;

    private final DeviceTokenService deviceTokenService;
    private final OrderNotificationService orderNotificationService;

    public DriverNotifications(
            final DeviceTokenService deviceTokenService,
            final OrderNotificationService orderNotificationService
    ) {
        this.deviceTokenService = deviceTokenService;
        this.orderNotificationService = orderNotificationService;
    }

    public void registerDevice(final Long driverId, final String token, final String platform) {
        deviceTokenService.register(NotificationOwner.driver(driverId), token, platformOf(platform));
    }

    public void unregisterDevice(final Long driverId, final String token) {
        deviceTokenService.unregister(NotificationOwner.driver(driverId), token);
    }

    public List<DriverNotificationView> findPage(final Long driverId, final int page, final int size) {
        final int safePage = Math.max(page, 0);
        final int safeSize = Math.clamp(size, 1, MAXIMUM_PAGE_SIZE);
        return orderNotificationService.findPage(NotificationOwner.driver(driverId), safePage, safeSize).stream()
                .map(DriverNotifications::toView)
                .toList();
    }

    public void read(final Long driverId, final Long notificationId) {
        orderNotificationService.read(NotificationOwner.driver(driverId), notificationId);
    }

    public void readAll(final Long driverId) {
        orderNotificationService.readAll(NotificationOwner.driver(driverId));
    }

    private static DevicePlatform platformOf(final String platform) {
        return Arrays.stream(DevicePlatform.values())
                .filter(candidate -> candidate.name().equals(platform))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST));
    }

    private static DriverNotificationView toView(final OrderNotification notification) {
        return new DriverNotificationView(
                notification.getId(),
                notification.getKind().name(),
                notification.getKind().headline(),
                notification.getAmount(),
                notification.getOrderId(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}
