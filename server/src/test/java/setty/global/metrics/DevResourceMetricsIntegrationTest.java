package setty.global.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import com.zaxxer.hikari.HikariDataSource;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.Metric;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryType;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
import org.springframework.boot.micrometer.metrics.autoconfigure.jvm.JvmMetricsAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DevResourceMetricsIntegrationTest {

    @Test
    void autoBindsHeapAndRealHikariPoolAndExportsTheirChangingGaugeValues() throws Exception {
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

        // JDBC transport alone is mocked. Hikari allocation/wait/release and Boot binding are real.
        DataSource jdbc = mock(DataSource.class);
        when(jdbc.getConnection()).thenAnswer(invocation -> {
            Connection connection = mock(Connection.class);
            when(connection.isValid(org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);
            when(connection.getAutoCommit()).thenReturn(true);
            return connection;
        });
        try (HikariDataSource pool = new HikariDataSource()) {
            pool.setDataSource(jdbc);
            pool.setPoolName("synthetic-pool");
            pool.setMaximumPoolSize(1);
            pool.setMinimumIdle(0);
            pool.setConnectionTimeout(30_000);
            new ApplicationContextRunner()
                    .withInitializer(new ConfigDataApplicationContextInitializer())
                    .withConfiguration(AutoConfigurations.of(MetricsAutoConfiguration.class,
                            CompositeMeterRegistryAutoConfiguration.class, OtlpMetricsExportAutoConfiguration.class,
                            JvmMetricsAutoConfiguration.class, DataSourcePoolMetricsAutoConfiguration.class))
                    .withUserConfiguration(MetricsConfig.class)
                    .withBean(DataSource.class, () -> pool)
                    .withPropertyValues("spring.profiles.active=dev",
                            "management.otlp.metrics.export.url=http://127.0.0.1:"
                                    + receiver.getAddress().getPort() + "/v1/metrics",
                            "management.otlp.metrics.export.step=1s")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        try (var executor = Executors.newSingleThreadExecutor()) {
                            java.util.concurrent.Future<Boolean> waiting;
                            try (Connection held = pool.getConnection()) {
                                waiting = executor.submit(() -> {
                                    try (Connection acquired = pool.getConnection()) {
                                        return true;
                                    }
                                });
                                await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                                    Map<String, Metric> metrics = metrics(received.get());
                                    assertThat(metrics).containsOnlyKeys("jvm.memory.used", "jvm.memory.max",
                                            "setty.http.requests.completed", "setty.http.requests.server.errors",
                                            "hikaricp.connections.active", "hikaricp.connections.max",
                                            "hikaricp.connections.pending", "hikaricp.connections.acquire",
                                            "hikaricp.connections.timeout");
                                    assertHeap(metrics);
                                    assertPool(metrics, 1, 1);
                                });
                            }
                            assertThat(waiting.get(5, TimeUnit.SECONDS)).isTrue();
                            // A later export must observe the released pool, not stale cumulative values.
                            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                                Map<String, Metric> exported = metrics(received.get());
                                assertPool(exported, 0, 0);
                                var acquire = exported.get("hikaricp.connections.acquire");
                                assertThat(acquire.hasExponentialHistogram()).isTrue();
                                assertThat(acquire.getExponentialHistogram().getDataPoints(0).getCount()).isEqualTo(2);
                                assertThat(acquire.getExponentialHistogram().getDataPoints(0).getSum()).isPositive();
                            });
                        }
                    });
        } finally {
            receiver.stop(0);
        }
    }

    private Map<String, Metric> metrics(ExportMetricsServiceRequest request) {
        assertThat(request).isNotNull();
        assertThat(request.getResourceMetricsList()).hasSize(1);
        var resource = request.getResourceMetrics(0);
        assertThat(attributes(resource.getResource().getAttributesList()))
                .containsEntry("service.name", "setty-backend")
                .containsEntry("deployment.environment.name", "dev");
        return resource.getScopeMetricsList().stream().flatMap(scope -> scope.getMetricsList().stream())
                .collect(Collectors.toMap(Metric::getName, metric -> metric));
    }

    private void assertHeap(Map<String, Metric> metrics) {
        List<String> heapIds = ManagementFactory.getMemoryPoolMXBeans().stream()
                .filter(bean -> bean.getType() == MemoryType.HEAP).map(bean -> bean.getName()).toList();
        for (String name : List.of("jvm.memory.used", "jvm.memory.max")) {
            Metric metric = metrics.get(name);
            assertThat(metric.getUnit()).isEqualTo("bytes");
            assertThat(metric.hasGauge()).isTrue();
            assertThat(metric.getGauge().getDataPointsList())
                    .extracting(point -> attributes(point.getAttributesList()).get("id"))
                    .containsExactlyInAnyOrderElementsOf(heapIds);
            assertThat(metric.getGauge().getDataPointsList()).allSatisfy(point -> {
                assertThat(attributes(point.getAttributesList())).containsOnlyKeys("area", "id")
                        .containsEntry("area", "heap");
                assertThat(point.getAsDouble()).isGreaterThanOrEqualTo(name.endsWith("max") ? -1 : 0);
            });
        }
    }

    private void assertPool(Map<String, Metric> metrics, double active, double pending) {
        Map<String, Double> expected = Map.of("hikaricp.connections.active", active,
                "hikaricp.connections.max", 1.0, "hikaricp.connections.pending", pending);
        expected.forEach((name, value) -> {
            Metric metric = metrics.get(name);
            assertThat(metric).isNotNull();
            assertThat(metric.hasGauge()).isTrue();
            assertThat(metric.getGauge().getDataPointsList()).hasSize(1);
            var point = metric.getGauge().getDataPoints(0);
            assertThat(attributes(point.getAttributesList())).containsExactlyEntriesOf(Map.of("pool", "synthetic-pool"));
            assertThat(point.getAsDouble()).isEqualTo(value);
        });
    }

    private Map<String, String> attributes(List<KeyValue> attributes) {
        return attributes.stream().collect(Collectors.toMap(KeyValue::getKey,
                attribute -> attribute.getValue().getStringValue()));
    }
}
