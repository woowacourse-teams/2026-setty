package setty.notification.device.presentation;

import jakarta.validation.constraints.NotBlank;

public record DeviceTokenDeleteRequest(@NotBlank String token) {
}
