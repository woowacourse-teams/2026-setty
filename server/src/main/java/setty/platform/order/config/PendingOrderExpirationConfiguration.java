package setty.platform.order.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PendingOrderExpirationProperties.class)
@EnableScheduling
public class PendingOrderExpirationConfiguration {

    @Bean
    public Clock pendingOrderExpirationClock() {
        return Clock.systemUTC();
    }
}
