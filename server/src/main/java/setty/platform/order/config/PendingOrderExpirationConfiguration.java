package setty.platform.order.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PendingOrderExpirationProperties.class)
public class PendingOrderExpirationConfiguration {

    @Bean
    public Clock pendingOrderExpirationClock() {
        return Clock.systemUTC();
    }
}
