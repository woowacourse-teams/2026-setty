package setty.dispatch;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "setty.dispatch")
public record DispatchProperties(
        String frontBaseUrl
) {
}
