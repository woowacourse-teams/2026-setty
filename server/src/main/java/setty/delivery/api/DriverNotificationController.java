package setty.delivery.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import setty.delivery.api.dto.DriverDeviceTokenDeleteRequest;
import setty.delivery.api.dto.DriverDeviceTokenRequest;
import setty.delivery.api.dto.DriverNotificationsResponse;
import setty.delivery.auth.domain.DeliveryMember;
import setty.global.auth.LoginDeliveryMember;
import setty.notification.DriverNotifications;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/delivery")
public class DriverNotificationController {

    private final DriverNotifications driverNotifications;

    @PutMapping("/device-token")
    public ResponseEntity<Void> registerDevice(
            @LoginDeliveryMember final DeliveryMember member,
            @Valid @RequestBody final DriverDeviceTokenRequest request
    ) {
        driverNotifications.registerDevice(member.getId(), request.token(), request.platform());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/device-token")
    public ResponseEntity<Void> unregisterDevice(
            @LoginDeliveryMember final DeliveryMember member,
            @Valid @RequestBody final DriverDeviceTokenDeleteRequest request
    ) {
        driverNotifications.unregisterDevice(member.getId(), request.token());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/notifications")
    public ResponseEntity<DriverNotificationsResponse> findMine(
            @LoginDeliveryMember final DeliveryMember member,
            @RequestParam(defaultValue = "0") final int page,
            @RequestParam(defaultValue = "20") final int size
    ) {
        return ResponseEntity.ok(new DriverNotificationsResponse(driverNotifications.findPage(member.getId(), page, size)));
    }

    @PostMapping("/notifications/read")
    public ResponseEntity<Void> readAll(@LoginDeliveryMember final DeliveryMember member) {
        driverNotifications.readAll(member.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/notifications/{notificationId}/read")
    public ResponseEntity<Void> read(
            @LoginDeliveryMember final DeliveryMember member,
            @PathVariable final Long notificationId
    ) {
        driverNotifications.read(member.getId(), notificationId);
        return ResponseEntity.noContent().build();
    }
}
