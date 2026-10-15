package setty.payment.infrastructure;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "setty.payment.toss")
public record TossPaymentProperties(
        @NotBlank String secretKey,
        @NotBlank String baseUrl,
        @DefaultValue("3s") Duration connectTimeout,
        // 토스는 결제 처리 API의 Read Timeout을 60초로 안내한다(docs.tosspayments.com/resources/glossary/timeout).
        @DefaultValue("60s") Duration readTimeout
) {
}
