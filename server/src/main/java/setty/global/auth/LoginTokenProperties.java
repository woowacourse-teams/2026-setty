package setty.global.auth;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "setty.auth")
public record LoginTokenProperties(
        @NotNull Duration tokenTtl
) {
}
