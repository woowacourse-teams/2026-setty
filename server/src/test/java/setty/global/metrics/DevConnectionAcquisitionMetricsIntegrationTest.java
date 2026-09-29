package setty.global.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import com.zaxxer.hikari.HikariDataSource;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.AggregationTemporality;
import io.opentelemetry.proto.metrics.v1.Metric;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.SQLTransientConnectionException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.metrics.DataSourcePoolMetricsAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.otlp.OtlpMetricsExportAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DevConnectionAcquisitionMetricsIntegrationTest {

    @Test
    void exportsShortAcquisitionsAndTimeoutAfterPoolHasReturnedToIdle() throws Exception {
        AtomicReference<ExportMetricsServiceRequest> received = new AtomicReference<>();
        HttpServer receiver = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        receiver.createContext("/v1/metrics", exchange -> {
            try (InputStream body = "gzip".equals(exchange.getRequestHeaders().getFirst("Content-Encoding"))
                    ? new GZIPInputStream(exchange.getRequestBody()) : exchange.getRequestBody()) {
                received.set(ExportMetricsServiceRequest.parseFrom(body));
                exchange.sendResponseHeaders(200, -1);
            } finally {
                exchange.close();
            }
        });
        receiver.start();

        // Only the JDBC transport is mocked; pool acquisition, timeout and metrics binding are real.
        DataSource jdbc = mock(DataSource.class);
        when(jdbc.getConnection()).thenAnswer(invocation -> {
            Connection connection = mock(Connection.class);
            when(connection.isValid(org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);
            when(connection.getAutoCommit()).thenReturn(true);
            return connection;
        });
        try (HikariDataSource pool = new HikariDataSource()) {
            pool.setDataSource(jdbc);
            pool.setPoolName("synthetic-acquisition-pool");
            pool.setMaximumPoolSize(1);
            pool.setMinimumIdle(0);
            pool.setConnectionTimeout(250);
            new ApplicationContextRunner()
                    .withInitializer(new ConfigDataApplicationContextInitializer())
                    .withConfiguration(AutoConfigurations.of(MetricsAutoConfiguration.class,
                            CompositeMeterRegistryAutoConfiguration.class, OtlpMetricsExportAutoConfiguration.class,
                            DataSourcePoolMetricsAutoConfiguration.class))
                    .withUserConfiguration(DevMetricsConfig.class)
                    .withBean(DataSource.class, () -> pool)
                    .withPropertyValues("spring.profiles.active=dev",
                            "management.otlp.metrics.export.url=http://127.0.0.1:"
                                    + receiver.getAddress().getPort() + "/v1/metrics",
                            "management.otlp.metrics.export.step=1s")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        try (Connection warmup = pool.getConnection()) {
                            // Establish the cumulative baseline before the measured events.
                        }
                        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                                assertEvents(received.get(), 1, 0));

                        for (int i = 0; i < 30; i++) {
                            try (Connection shortUse = pool.getConnection()) {
                                // Each connection is returned immediately, before the next acquisition.
                            }
                        }
                        try (Connection held = pool.getConnection()) {
                            assertThatThrownBy(pool::getConnection).isInstanceOf(SQLTransientConnectionException.class);
                        }

                        // Hikari includes timed-out acquisition attempts in the acquire Timer as well.
                        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                            assertEvents(received.get(), 33, 1);
                            assertIdle(received.get());
                            assertThat(metric(received.get(), "hikaricp.connections.acquire")
                                    .getExponentialHistogram().getDataPoints(0).getSum()).isGreaterThanOrEqualTo(200);
                        });
                        long firstExportTime = metric(received.get(), "hikaricp.connections.timeout")
                                .getSum().getDataPoints(0).getTimeUnixNano();
                        // A later export still retains the events, even though both gauges read zero.
                        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                            var request = received.get();
                            assertEvents(request, 33, 1);
                            assertIdle(request);
                            assertThat(metric(request, "hikaricp.connections.timeout")
                                    .getSum().getDataPoints(0).getTimeUnixNano()).isGreaterThan(firstExportTime);
                        });
                    });
        } finally {
            receiver.stop(0);
        }
    }

    private void assertEvents(ExportMetricsServiceRequest request, long attempts, double timeouts) {
        Metric acquire = metric(request, "hikaricp.connections.acquire");
        assertThat(acquire.getUnit()).isEqualTo("milliseconds");
        assertThat(acquire.hasExponentialHistogram()).isTrue();
        var histogram = acquire.getExponentialHistogram();
        assertThat(histogram.getAggregationTemporality())
                .isEqualTo(AggregationTemporality.AGGREGATION_TEMPORALITY_CUMULATIVE);
        assertThat(histogram.getDataPointsList()).hasSize(1);
        var point = histogram.getDataPoints(0);
        assertPoolTag(point.getAttributesList());
        assertThat(point.getCount()).isEqualTo(attempts);
        assertThat(point.getPositive().getBucketCountsList().stream().mapToLong(Long::longValue).sum()
                + point.getZeroCount()).isEqualTo(attempts);

        Metric timeout = metric(request, "hikaricp.connections.timeout");
        assertThat(timeout.hasSum()).isTrue();
        assertThat(timeout.getSum().getIsMonotonic()).isTrue();
        assertThat(timeout.getSum().getAggregationTemporality())
                .isEqualTo(AggregationTemporality.AGGREGATION_TEMPORALITY_CUMULATIVE);
        assertThat(timeout.getSum().getDataPointsList()).hasSize(1);
        var timeoutPoint = timeout.getSum().getDataPoints(0);
        assertPoolTag(timeoutPoint.getAttributesList());
        assertThat(timeoutPoint.getAsDouble()).isEqualTo(timeouts);
    }

    private void assertIdle(ExportMetricsServiceRequest request) {
        for (String name : List.of("hikaricp.connections.active", "hikaricp.connections.pending")) {
            assertThat(metric(request, name).getGauge().getDataPoints(0).getAsDouble()).isZero();
        }
    }

    private Metric metric(ExportMetricsServiceRequest request, String name) {
        assertThat(request).isNotNull();
        assertThat(request.getResourceMetricsList()).hasSize(1);
        var resource = request.getResourceMetrics(0);
        assertThat(attributes(resource.getResource().getAttributesList()))
                .containsEntry("service.name", "setty-backend")
                .containsEntry("deployment.environment.name", "dev");
        return resource.getScopeMetricsList().stream().flatMap(scope -> scope.getMetricsList().stream())
                .filter(metric -> metric.getName().equals(name)).findFirst().orElseThrow();
    }

    private void assertPoolTag(List<KeyValue> tags) {
        assertThat(attributes(tags)).containsExactlyEntriesOf(Map.of("pool", "synthetic-acquisition-pool"));
    }

    private Map<String, String> attributes(List<KeyValue> attributes) {
        return attributes.stream().collect(Collectors.toMap(KeyValue::getKey,
                attribute -> attribute.getValue().getStringValue()));
    }
}
