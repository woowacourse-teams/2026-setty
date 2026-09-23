package setty.platform.order.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "setty.order.pending-expiration")
public record PendingOrderExpirationProperties(
        @NotNull Duration timeout
) {
}
