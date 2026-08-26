package setty.platform.listing.storage;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "setty.storage.listing-images")
public record ListingImageStorageProperties(
        @NotBlank String region,
        @NotBlank String bucket,
        @NotBlank String publicBaseUrl
) {
}
