package setty.delivery.api.dto;

import jakarta.validation.constraints.NotBlank;

public record DriverDeviceTokenRequest(@NotBlank String token, @NotBlank String platform) {
}
