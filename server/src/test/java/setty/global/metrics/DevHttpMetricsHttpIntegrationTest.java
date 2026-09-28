package setty.global.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.AggregationTemporality;
import io.opentelemetry.proto.metrics.v1.ExponentialHistogramDataPoint;
import io.opentelemetry.proto.metrics.v1.Metric;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

class DevHttpMetricsHttpIntegrationTest {

    @Test
    void exportsRealHttpRequestsAsBoundedCumulativeHistograms() throws Exception {
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

        try (var context = new SpringApplicationBuilder(ProbeApplication.class).profiles("dev").run(
                "--server.address=127.0.0.1", "--server.port=0",
                "--management.otlp.metrics.export.url=http://127.0.0.1:"
                        + receiver.getAddress().getPort() + "/v1/metrics",
                "--management.otlp.metrics.export.step=1s");
                HttpClient client = HttpClient.newHttpClient()) {
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            assertThat(get(client, port, "/metrics-probe/one?userId=synthetic-user")).isEqualTo(200);
            assertThat(get(client, port, "/metrics-probe/two")).isEqualTo(200);
            assertThat(client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/metrics-probe/three"))
                    .timeout(Duration.ofSeconds(5)).POST(HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(200);
            assertThat(get(client, port, "/metrics-probe/other")).isEqualTo(200);
            assertThat(get(client, port, "/metrics-probe/server-error")).isEqualTo(503);
            assertThat(get(client, port, "/metrics-probe/missing/path")).isEqualTo(404);
            assertThat(get(client, port, "/metrics-probe/another/missing/path")).isEqualTo(404);

            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                ExportMetricsServiceRequest request = received.get();
                assertThat(request).isNotNull();
                assertThat(request.getResourceMetricsList()).hasSize(1);
                var resourceMetrics = request.getResourceMetrics(0);
                assertThat(attributes(resourceMetrics.getResource().getAttributesList()))
                        .containsEntry("service.name", "setty-backend")
                        .containsEntry("deployment.environment.name", "dev");
                List<Metric> metrics = resourceMetrics.getScopeMetricsList().stream()
                        .flatMap(scope -> scope.getMetricsList().stream()).toList();
                assertThat(metrics).extracting(Metric::getName)
                        .containsExactlyInAnyOrder("http.server.requests", "jvm.memory.used", "jvm.memory.max");
                Metric metric = metrics.stream().filter(m -> m.getName().equals("http.server.requests"))
                        .findFirst().orElseThrow();
                assertThat(metric.getName()).isEqualTo("http.server.requests");
                assertThat(metric.getUnit()).isEqualTo("milliseconds");
                assertThat(metric.hasExponentialHistogram()).isTrue();
                assertThat(metric.getExponentialHistogram().getAggregationTemporality())
                        .isEqualTo(AggregationTemporality.AGGREGATION_TEMPORALITY_CUMULATIVE);
                List<ExponentialHistogramDataPoint> points = metric.getExponentialHistogram().getDataPointsList();
                assertThat(points).hasSize(5).allSatisfy(point -> {
                    assertThat(attributes(point.getAttributesList())).containsOnlyKeys("uri", "method", "outcome");
                    assertThat(point.getSum()).isPositive();
                    assertThat(point.getPositive().getBucketCountsList().stream().mapToLong(Long::longValue).sum()
                            + point.getZeroCount()).isEqualTo(point.getCount());
                });
                assertThat(points.stream().collect(Collectors.toMap(
                        point -> attributes(point.getAttributesList()),
                        ExponentialHistogramDataPoint::getCount)))
                        .containsExactlyInAnyOrderEntriesOf(Map.of(
                                Map.of("uri", "/metrics-probe/{id}", "method", "GET", "outcome", "SUCCESS"), 2L,
                                Map.of("uri", "/metrics-probe/{id}", "method", "POST", "outcome", "SUCCESS"), 1L,
                                Map.of("uri", "/metrics-probe/other", "method", "GET", "outcome", "SUCCESS"), 1L,
                                Map.of("uri", "/metrics-probe/server-error", "method", "GET", "outcome", "SERVER_ERROR"), 1L,
                                Map.of("uri", "/**", "method", "GET", "outcome", "CLIENT_ERROR"), 2L));
            });
        } finally {
            receiver.stop(0);
        }
    }

    private int get(HttpClient client, int port, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5)).header("X-Request-Id", "synthetic-request-id").GET().build(),
                HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private Map<String, String> attributes(List<KeyValue> attributes) {
        return attributes.stream().collect(Collectors.toMap(KeyValue::getKey,
                attribute -> attribute.getValue().getStringValue()));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @Import({DevMetricsConfig.class, ProbeController.class})
    static class ProbeApplication {
    }

    @RestController
    static class ProbeController {

        @GetMapping("/metrics-probe/{id}")
        String success(@PathVariable String id) {
            return "synthetic response";
        }

        @PostMapping("/metrics-probe/{id}")
        String post(@PathVariable String id) {
            return "synthetic response";
        }

        @GetMapping("/metrics-probe/other")
        String other() {
            return "synthetic response";
        }

        @GetMapping("/metrics-probe/server-error")
        ResponseEntity<Void> serverError() {
            return ResponseEntity.status(503).build();
        }
    }
}
