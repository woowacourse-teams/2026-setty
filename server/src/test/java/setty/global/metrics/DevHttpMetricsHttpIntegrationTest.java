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
import java.util.concurrent.CompletableFuture;
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
            // No HTTP requests yet: publish a real zero baseline, including the error counter.
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertCounts(received.get(), 0, 0));
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
                        .containsExactlyInAnyOrder("http.server.requests", "jvm.memory.used", "jvm.memory.max",
                                "setty.http.requests.completed", "setty.http.requests.server.errors");
                assertCounts(request, 7, 1);
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

            // Once the series has a published baseline, 30 additional requests must add exactly 30.
            long baseline = successCount(received.get());
            for (int i = 0; i < 30; i++) {
                assertThat(get(client, port, "/metrics-probe/burst-" + i)).isEqualTo(200);
            }
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                    assertThat(successCount(received.get()) - baseline).isEqualTo(30));

            // A new route's first 5xx must increment the already published aggregate counter.
            assertThat(get(client, port, "/actuator/health")).isEqualTo(200);
            assertThat(get(client, port, "/metrics-probe/new-server-error")).isEqualTo(503);
            // Async redispatch/completion must count one completed response, not two.
            assertThat(get(client, port, "/metrics-probe/async-error")).isEqualTo(503);
            // A thrown exception and its error dispatch must also count exactly once.
            assertThat(get(client, port, "/metrics-probe/unhandled-error")).isEqualTo(500);
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertCounts(received.get(), 40, 4));
            long exportedAt = counter(received.get(), "setty.http.requests.server.errors")
                    .getSum().getDataPoints(0).getTimeUnixNano();
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                assertThat(counter(received.get(), "setty.http.requests.server.errors")
                        .getSum().getDataPoints(0).getTimeUnixNano()).isGreaterThan(exportedAt);
                assertCounts(received.get(), 40, 4);
            });
        } finally {
            receiver.stop(0);
        }
    }

    private void assertCounts(ExportMetricsServiceRequest request, long completed, long errors) {
        assertThat(request).isNotNull();
        for (var expected : Map.of("setty.http.requests.completed", completed,
                "setty.http.requests.server.errors", errors).entrySet()) {
            var sum = counter(request, expected.getKey()).getSum();
            assertThat(sum.getIsMonotonic()).isTrue();
            assertThat(sum.getAggregationTemporality())
                    .isEqualTo(AggregationTemporality.AGGREGATION_TEMPORALITY_CUMULATIVE);
            assertThat(sum.getDataPointsList()).hasSize(1);
            var point = sum.getDataPoints(0);
            assertThat(point.getAttributesList()).isEmpty();
            assertThat(point.getAsDouble()).isEqualTo(expected.getValue().doubleValue());
        }
    }

    private Metric counter(ExportMetricsServiceRequest request, String name) {
        return request.getResourceMetricsList().stream().flatMap(resource -> resource.getScopeMetricsList().stream())
                .flatMap(scope -> scope.getMetricsList().stream()).filter(metric -> metric.getName().equals(name))
                .findFirst().orElseThrow();
    }

    private long successCount(ExportMetricsServiceRequest request) {
        return request.getResourceMetricsList().stream().flatMap(resource -> resource.getScopeMetricsList().stream())
                .flatMap(scope -> scope.getMetricsList().stream())
                .filter(metric -> metric.getName().equals("http.server.requests"))
                .flatMap(metric -> metric.getExponentialHistogram().getDataPointsList().stream())
                .filter(point -> attributes(point.getAttributesList()).equals(
                        Map.of("uri", "/metrics-probe/{id}", "method", "GET", "outcome", "SUCCESS")))
                .mapToLong(ExponentialHistogramDataPoint::getCount).findFirst().orElseThrow();
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

        @GetMapping("/metrics-probe/new-server-error")
        ResponseEntity<Void> newServerError() {
            return ResponseEntity.status(503).build();
        }

        @GetMapping("/metrics-probe/async-error")
        CompletableFuture<ResponseEntity<Void>> asyncError() {
            return CompletableFuture.supplyAsync(() -> ResponseEntity.status(503).build());
        }

        @GetMapping("/metrics-probe/unhandled-error")
        String unhandledError() {
            throw new IllegalStateException("synthetic metrics probe failure");
        }
    }
}
