package setty.global.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLTransientConnectionException;
import java.time.Duration;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DevConnectionTimeoutTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class));

    @Test
    void devStopsWaitingForAnOccupiedPoolAndCanAcquireAgainAfterRelease() {
        contextRunner.withPropertyValues("spring.profiles.active=dev").run(context -> {
            HikariDataSource pool = context.getBean(HikariDataSource.class);
            assertThat(pool.getConnectionTimeout()).isEqualTo(5000);
            assertThat(pool.getValidationTimeout()).isEqualTo(1000);

            // Exercise the configured Hikari pool; no local or remote database is needed.
            DataSource jdbc = mock(DataSource.class);
            when(jdbc.getConnection(pool.getUsername(), pool.getPassword())).thenAnswer(invocation -> {
                Connection connection = mock(Connection.class);
                when(connection.isValid(anyInt())).thenReturn(true);
                when(connection.getAutoCommit()).thenReturn(true);
                return connection;
            });
            pool.setDataSource(jdbc);
            pool.setMaximumPoolSize(1);
            pool.setMinimumIdle(0);
            try (Connection held = pool.getConnection()) {
                long start = System.nanoTime();
                assertThatThrownBy(pool::getConnection).isInstanceOf(SQLTransientConnectionException.class);
                assertThat(Duration.ofNanos(System.nanoTime() - start).toMillis()).isBetween(4500L, 15000L);
            }
            try (Connection recovered = pool.getConnection()) {
                assertThat(recovered.isValid(1)).isTrue();
            }
        });
    }

    @Test
    void prodKeepsItsExistingConnectionAndValidationTimeouts() {
        contextRunner.withPropertyValues("spring.profiles.active=prod").run(context -> {
            HikariDataSource pool = context.getBean(HikariDataSource.class);
            assertThat(pool.getConnectionTimeout()).isEqualTo(30000);
            assertThat(pool.getValidationTimeout()).isEqualTo(5000);
        });
    }
}
