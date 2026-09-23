package setty.platform.listing.presentation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import setty.platform.listing.domain.ConditionGrade;
import setty.platform.listing.domain.ListingCategory;

public record UpdateListingRequest(
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 1_000) String description,
        @NotNull @PositiveOrZero @Max(100_000_000) Integer price,
        @NotNull ListingCategory category,
        @NotNull ConditionGrade conditionGrade,
        @NotNull @Valid DimensionsRequest dimensions,
        @NotNull @Size(max = 5) List<@Positive Long> retainedImageIds
) {
}
