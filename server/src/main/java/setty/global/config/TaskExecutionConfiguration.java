package setty.global.config;

import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class TaskExecutionConfiguration {

    private static final String APPLICATION_TASK_EXECUTOR = "applicationTaskExecutor";
    private static final String ASYNC_DEFAULT_EXECUTOR = "taskExecutor";

    @Bean(name = {APPLICATION_TASK_EXECUTOR, ASYNC_DEFAULT_EXECUTOR})
    public ThreadPoolTaskExecutor applicationTaskExecutor(final ThreadPoolTaskExecutorBuilder builder) {
        return builder.build();
    }
}
