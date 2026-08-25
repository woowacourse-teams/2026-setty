package setty.platform.listing.presentation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import setty.platform.listing.domain.Dimensions;

public record DimensionsRequest(
        @NotNull @Min(1) @Max(1_000) Integer widthCm,
        @NotNull @Min(1) @Max(1_000) Integer depthCm,
        @NotNull @Min(1) @Max(1_000) Integer heightCm
) {
    public Dimensions toDomain() {
        return Dimensions.of(widthCm, depthCm, heightCm);
    }
}
