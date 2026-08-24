package setty.prototype.dto.listing;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import setty.prototype.web.StrictPriceDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record CreateListingRequest(
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 500) String description,
        @NotNull @PositiveOrZero @Max(99_999_999)
        @JsonDeserialize(using = StrictPriceDeserializer.class)
        Integer price,
        @NotBlank @Size(max = 50) String pickupTimeText,
        @NotNull Boolean canHelpMove
) {
}
