package setty.platform.order.controller.dto;

import jakarta.validation.constraints.NotNull;

public record OrderCreateRequest(
        @NotNull Long listingId
) {
}
