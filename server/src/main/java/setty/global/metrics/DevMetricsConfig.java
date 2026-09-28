package setty.global.metrics;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import java.util.List;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("dev")
public class DevMetricsConfig {

    @Bean
    MeterRegistryCustomizer<OtlpMeterRegistry> devMetrics() {
        return registry -> registry.config()
                .meterFilter(MeterFilter.denyUnless(DevMetricsConfig::isAllowed))
                .meterFilter(new MeterFilter() {
                    @Override
                    public Meter.Id map(Meter.Id id) {
                        return switch (id.getName()) {
                            // uri는 원본 URL이 아니라 Spring HTTP 관측이 제공하는 매핑 패턴이다.
                            case "http.server.requests" -> id.replaceTags(
                                    List.of(tag(id, "uri"), tag(id, "method"), tag(id, "outcome")));
                            // pool별 게이지를 하나로 합치면 합계가 아니라 첫 번째 값만 남는다.
                            case "jvm.memory.used", "jvm.memory.max" -> id.replaceTags(
                                    List.of(tag(id, "area"), tag(id, "id")));
                            case "hikaricp.connections.active", "hikaricp.connections.max",
                                    "hikaricp.connections.pending" -> id.replaceTags(List.of(tag(id, "pool")));
                            default -> id;
                        };
                    }
                });
    }

    private static boolean isAllowed(Meter.Id id) {
        return switch (id.getName()) {
            case "http.server.requests", "hikaricp.connections.active", "hikaricp.connections.max",
                    "hikaricp.connections.pending" -> true;
            case "jvm.memory.used", "jvm.memory.max" -> "heap".equals(id.getTag("area"));
            default -> false;
        };
    }

    private static Tag tag(Meter.Id id, String key) {
        String value = id.getTag(key);
        return Tag.of(key, value == null ? "UNKNOWN" : value);
    }
}
