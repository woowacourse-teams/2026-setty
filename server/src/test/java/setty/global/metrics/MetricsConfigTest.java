package setty.global.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.registry.otlp.OtlpConfig;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MetricsConfigTest {

    @Test
    void keepsHeapAndConnectionPoolDimensionsWithoutExportingOtherResourceMetrics() {
        OtlpConfig config = key -> "otlp.enabled".equals(key) ? "false" : null;
        OtlpMeterRegistry registry = new OtlpMeterRegistry(config, Clock.SYSTEM);
        try {
            new MetricsConfig().metrics().customize(registry);
            AtomicInteger first = new AtomicInteger(10);
            AtomicInteger second = new AtomicInteger(20);
            for (String name : new String[]{"jvm.memory.used", "jvm.memory.max"}) {
                Gauge.builder(name, first, AtomicInteger::doubleValue)
                        .tags("area", "heap", "id", "synthetic-young", "requestId", "synthetic-request")
                        .register(registry);
                Gauge.builder(name, second, AtomicInteger::doubleValue)
                        .tags("area", "heap", "id", "synthetic-old").register(registry);
                Gauge.builder(name, first, AtomicInteger::doubleValue)
                        .tags("area", "nonheap", "id", "synthetic-metaspace").register(registry);
            }
            for (String name : new String[]{"hikaricp.connections.active", "hikaricp.connections.max",
                    "hikaricp.connections.pending"}) {
                Gauge.builder(name, first, AtomicInteger::doubleValue)
                        .tags("pool", "synthetic-first", "userId", "synthetic-user").register(registry);
                Gauge.builder(name, second, AtomicInteger::doubleValue)
                        .tag("pool", "synthetic-second").register(registry);
            }
            for (String name : new String[]{"jvm.memory.committed", "jvm.gc.pause", "jvm.threads.live",
                    "jdbc.connections.active", "hikaricp.connections.idle", "hikaricp.connections.usage"}) {
                Gauge.builder(name, first, AtomicInteger::doubleValue).register(registry);
            }
            assertThat(registry.getMeters()).hasSize(10);
            assertThat(registry.get("jvm.memory.used").tag("id", "synthetic-young").gauge().value()).isEqualTo(10);
            assertThat(registry.get("jvm.memory.used").tag("id", "synthetic-old").gauge().value()).isEqualTo(20);
            assertThat(registry.get("hikaricp.connections.active").tag("pool", "synthetic-second")
                    .gauge().value()).isEqualTo(20);
            assertThat(registry.getMeters()).allSatisfy(meter -> {
                if (meter.getId().getName().startsWith("jvm.")) {
                    assertThat(meter.getId().getTags()).extracting(Tag::getKey)
                            .containsExactlyInAnyOrder("area", "id");
                } else {
                    assertThat(meter.getId().getTags()).extracting(Tag::getKey).containsExactly("pool");
                }
            });
        } finally {
            registry.close();
        }
    }

    @Test
    void keepsAcquisitionEventsSeparateByPoolWithoutUnboundedTags() {
        OtlpConfig config = key -> "otlp.enabled".equals(key) ? "false" : null;
        OtlpMeterRegistry registry = new OtlpMeterRegistry(config, Clock.SYSTEM);
        try {
            new MetricsConfig().metrics().customize(registry);
            for (String pool : new String[]{"first", "second"}) {
                for (String request : new String[]{"request-a", "request-b"}) {
                    Timer.builder("hikaricp.connections.acquire")
                            .tags("pool", pool, "requestId", request).register(registry)
                            .record(pool.equals("first") ? 10 : 20, TimeUnit.MILLISECONDS);
                    Counter.builder("hikaricp.connections.timeout")
                            .tags("pool", pool, "requestId", request).register(registry)
                            .increment(pool.equals("first") ? 1 : 2);
                }
            }
            assertThat(registry.getMeters()).hasSize(4).allSatisfy(meter ->
                    assertThat(meter.getId().getTags()).extracting(Tag::getKey).containsExactly("pool"));
            for (String pool : new String[]{"first", "second"}) {
                Timer timer = registry.get("hikaricp.connections.acquire").tag("pool", pool).timer();
                assertThat(timer.count()).isEqualTo(2);
                assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(pool.equals("first") ? 20 : 40);
                assertThat(registry.get("hikaricp.connections.timeout").tag("pool", pool).counter().count())
                        .isEqualTo(pool.equals("first") ? 2 : 4);
            }
        } finally {
            registry.close();
        }
    }

    @Test
    void exportsOnlyHttpRequestsGroupedByRouteMethodAndOutcome() {
        OtlpConfig config = key -> "otlp.enabled".equals(key) ? "false" : null;
        OtlpMeterRegistry registry = new OtlpMeterRegistry(config, Clock.SYSTEM);
        try {
            new MetricsConfig().metrics().customize(registry);

            Timer.builder("http.server.requests")
                    .tags("outcome", "SUCCESS", "uri", "/api/listings/{id}", "method", "GET",
                            "userId", "synthetic-user-a", "requestId", "synthetic-request-a", "status", "200")
                    .register(registry)
                    .record(10, TimeUnit.MILLISECONDS);
            Timer.builder("http.server.requests")
                    .tags("outcome", "SUCCESS", "uri", "/api/listings/{id}", "method", "GET",
                            "userId", "synthetic-user-b", "requestId", "synthetic-request-b", "status", "200")
                    .register(registry)
                    .record(15, TimeUnit.MILLISECONDS);
            Timer.builder("http.server.requests")
                    .tags("outcome", "SUCCESS", "uri", "/api/orders", "method", "POST")
                    .register(registry)
                    .record(20, TimeUnit.MILLISECONDS);
            Timer.builder("http.server.requests")
                    .tags("outcome", "SERVER_ERROR", "uri", "/api/orders", "method", "POST")
                    .register(registry)
                    .record(30, TimeUnit.MILLISECONDS);
            registry.counter("jvm.gc.pause").increment();

            assertThat(registry.getMeters()).hasSize(3);
            assertThat(registry.get("http.server.requests").tags("outcome", "SUCCESS", "uri", "/api/listings/{id}")
                    .timer().count()).isEqualTo(2);
            assertThat(registry.get("http.server.requests").tags("outcome", "SUCCESS", "uri", "/api/orders")
                    .timer().count()).isEqualTo(1);
            assertThat(registry.get("http.server.requests").tag("outcome", "SERVER_ERROR").timer().count())
                    .isEqualTo(1);
            assertThat(registry.getMeters())
                    .allSatisfy(meter -> assertThat(meter.getId().getTags())
                            .containsExactlyInAnyOrder(Tag.of("uri", meter.getId().getTag("uri")),
                                    Tag.of("method", meter.getId().getTag("method")),
                                    Tag.of("outcome", meter.getId().getTag("outcome"))));
        } finally {
            registry.close();
        }
    }
}
