package setty.prototype.dto.listing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateListingRequest(
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 500) String description,
        @NotBlank @Size(max = 50) String pickupTimeText,
        @NotNull Boolean canHelpMove
) {
}
