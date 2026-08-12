package setty.common.operator;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "setty.operator")
public record OperatorAuthProperties(
        String secret
) {
}
