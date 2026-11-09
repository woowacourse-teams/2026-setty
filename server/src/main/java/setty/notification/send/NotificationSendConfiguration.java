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
    public static final String ORDER_DISPATCH_EXECUTOR = "orderNotificationDispatchExecutor";
    private static final int DISPATCH_CONCURRENCY = 1;
    private static final int ORDER_DISPATCH_CONCURRENCY = 2;

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
        return dispatchExecutor(DISPATCH_CONCURRENCY, "notification-dispatch-");
    }

    // 주문 알림은 FCM 응답을 기다리는 동안 주문·배송 리스너의 공용 실행기를 막지 않게, 키워드 대량 발송에 밀리지 않게 따로 돈다.
    @Bean(ORDER_DISPATCH_EXECUTOR)
    public ThreadPoolTaskExecutor orderNotificationDispatchExecutor() {
        return dispatchExecutor(ORDER_DISPATCH_CONCURRENCY, "order-notification-");
    }

    private static ThreadPoolTaskExecutor dispatchExecutor(final int concurrency, final String threadNamePrefix) {
        final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(concurrency);
        executor.setMaxPoolSize(concurrency);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
