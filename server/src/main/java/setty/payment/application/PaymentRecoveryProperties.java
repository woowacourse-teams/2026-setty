package setty.payment.application;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 결제 복구 배치(결과 모르는 승인 정리·환불) 설정.
 * staleAfter는 주문 만료(6분)와 토스 읽기 제한(60초)이 모두 지난 뒤여야 진행 중인 승인과 겹치지 않는다.
 */
@Validated
@ConfigurationProperties(prefix = "setty.payment.recovery")
public record PaymentRecoveryProperties(
        @NotNull @DefaultValue("10m") Duration staleAfter,
        @Positive @DefaultValue("20") int batchSize,
        @Positive @DefaultValue("5") int maxRefundAttempts
) {
}
