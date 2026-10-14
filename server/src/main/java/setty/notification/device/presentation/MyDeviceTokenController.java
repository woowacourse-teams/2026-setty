package setty.notification.device.presentation;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import setty.global.auth.LoginMember;
import setty.notification.device.application.DeviceTokenService;

@RestController
@RequestMapping("/api/me/device-token")
public class MyDeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    public MyDeviceTokenController(final DeviceTokenService deviceTokenService) {
        this.deviceTokenService = deviceTokenService;
    }

    @PutMapping
    public ResponseEntity<Void> register(
            @LoginMember final Long memberId,
            @Valid @RequestBody final DeviceTokenRequest request
    ) {
        deviceTokenService.register(memberId, request.token(), request.platform());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> unregister(
            @LoginMember final Long memberId,
            @Valid @RequestBody final DeviceTokenDeleteRequest request
    ) {
        deviceTokenService.unregister(memberId, request.token());
        return ResponseEntity.noContent().build();
    }
}
