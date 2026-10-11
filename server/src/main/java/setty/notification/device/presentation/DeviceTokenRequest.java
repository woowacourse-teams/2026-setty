package setty.notification.device.presentation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import setty.notification.device.domain.DevicePlatform;

public record DeviceTokenRequest(@NotBlank String token, @NotNull DevicePlatform platform) {
}
