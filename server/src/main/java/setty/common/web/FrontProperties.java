package setty.common.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "setty.front")
public record FrontProperties(
        String baseUrl
) {
}
