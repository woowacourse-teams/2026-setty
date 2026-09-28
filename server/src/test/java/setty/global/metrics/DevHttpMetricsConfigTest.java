package setty.global.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.registry.otlp.OtlpConfig;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class DevHttpMetricsConfigTest {

    @Test
    void exportsOnlyHttpRequestsGroupedByOutcome() {
        OtlpConfig config = key -> null;
        OtlpMeterRegistry registry = new OtlpMeterRegistry(config, Clock.SYSTEM);
        try {
            new DevHttpMetricsConfig().devHttpMetricsOnly().customize(registry);

            Timer.builder("http.server.requests")
                    .tags("outcome", "SUCCESS", "uri", "/api/listings/{id}", "method", "GET")
                    .register(registry)
                    .record(10, TimeUnit.MILLISECONDS);
            Timer.builder("http.server.requests")
                    .tags("outcome", "SUCCESS", "uri", "/api/orders", "method", "POST")
                    .register(registry)
                    .record(20, TimeUnit.MILLISECONDS);
            Timer.builder("http.server.requests")
                    .tags("outcome", "SERVER_ERROR", "uri", "/api/orders", "method", "POST")
                    .register(registry)
                    .record(30, TimeUnit.MILLISECONDS);
            registry.counter("jvm.gc.pause").increment();

            assertThat(registry.getMeters()).hasSize(2);
            assertThat(registry.get("http.server.requests").tag("outcome", "SUCCESS").timer().count()).isEqualTo(2);
            assertThat(registry.get("http.server.requests").tag("outcome", "SERVER_ERROR").timer().count())
                    .isEqualTo(1);
            assertThat(registry.getMeters())
                    .allSatisfy(meter -> assertThat(meter.getId().getTags())
                            .containsExactly(Tag.of("outcome", meter.getId().getTag("outcome"))));
        } finally {
            registry.close();
        }
    }
}
