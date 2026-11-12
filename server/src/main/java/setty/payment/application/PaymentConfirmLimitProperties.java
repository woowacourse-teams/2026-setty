package setty.payment.application;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 토스 승인 호출 한도(토큰 버킷).
 * capacity는 한 번에 몰려도 받는 승인 수, refillPerSecond는 평균 초당 승인 수 상한이다.
 * 토스 응답이 읽기 제한(60초)까지 늦어지는 최악의 경우에도 동시에 기다리는 승인은
 * capacity + refillPerSecond × 60 = 140건 이하로, Tomcat 기본 스레드(200)를 다 쓰지 않는다.
 */
@Validated
@ConfigurationProperties(prefix = "setty.payment.confirm-limit")
public record PaymentConfirmLimitProperties(
        @Positive @DefaultValue("20") long capacity,
        @Positive @DefaultValue("2") double refillPerSecond
) {
}
