package setty.notification.send;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(NotificationSendProperties.class)
public class NotificationSendConfiguration {

    public static final String SEND_EXECUTOR = "notificationSendExecutor";
    public static final String DISPATCH_EXECUTOR = "notificationDispatchExecutor";
    private static final int DISPATCH_CONCURRENCY = 1;

    @Bean(SEND_EXECUTOR)
    public ThreadPoolTaskExecutor notificationSendExecutor(final NotificationSendProperties properties) {
        final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.sendConcurrency());
        executor.setMaxPoolSize(properties.sendConcurrency());
        executor.setThreadNamePrefix("notification-send-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    @Bean(DISPATCH_EXECUTOR)
    public ThreadPoolTaskExecutor notificationDispatchExecutor() {
        final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(DISPATCH_CONCURRENCY);
        executor.setMaxPoolSize(DISPATCH_CONCURRENCY);
        executor.setThreadNamePrefix("notification-dispatch-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
