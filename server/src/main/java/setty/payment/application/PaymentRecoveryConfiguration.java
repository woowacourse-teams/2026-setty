package setty.payment.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PaymentRecoveryProperties.class)
public class PaymentRecoveryConfiguration {
}
