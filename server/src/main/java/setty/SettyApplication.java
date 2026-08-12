package setty;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import setty.common.notification.DiscordNotificationProperties;
import setty.common.operator.OperatorAuthProperties;
import setty.common.s3.S3Properties;
import setty.common.web.FrontProperties;

@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties({
        S3Properties.class,
        OperatorAuthProperties.class,
        FrontProperties.class,
        DiscordNotificationProperties.class
})
public class SettyApplication {
    public static void main(String[] args) {
        SpringApplication.run(SettyApplication.class, args);
    }
}
