package setty.payment.infrastructure;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "setty.payment.toss")
public record TossPaymentProperties(
        @NotBlank String secretKey,
        @NotBlank String baseUrl
) {
}
