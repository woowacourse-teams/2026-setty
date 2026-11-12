package setty.delivery.api.dto;

import jakarta.validation.constraints.NotBlank;

public record DriverDeviceTokenDeleteRequest(@NotBlank String token) {
}
