package setty.deliveryfee.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DeliveryFeeProperties.class)
public class DeliveryFeeConfiguration {
}
