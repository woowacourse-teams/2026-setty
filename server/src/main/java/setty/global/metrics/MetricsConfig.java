package setty.global.metrics;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile({"dev", "prod"})
@ConditionalOnProperty(name = "management.otlp.metrics.export.enabled", havingValue = "true")
public class MetricsConfig {

    @Bean
    HttpRequestCountHandler httpRequestCountHandler(MeterRegistry registry) {
        return new HttpRequestCountHandler(registry);
    }

    @Bean
    MeterRegistryCustomizer<OtlpMeterRegistry> metrics() {
        return registry -> registry.config()
                .meterFilter(MeterFilter.denyUnless(MetricsConfig::isAllowed))
                .meterFilter(new MeterFilter() {
                    @Override
                    public Meter.Id map(Meter.Id id) {
                        return switch (id.getName()) {
                            // uri는 원본 URL이 아니라 Spring HTTP 관측이 제공하는 매핑 패턴이다.
                            // 첫 실패도 기존 경로의 누적 히스토그램에 기록되도록 outcome을 합친다.
                            case "http.server.requests" -> id.replaceTags(
                                    List.of(tag(id, "uri"), tag(id, "method")));
                            case "setty.http.requests.completed", "setty.http.requests.server.errors" ->
                                    id.replaceTags(List.of());
                            // pool별 게이지를 하나로 합치면 합계가 아니라 첫 번째 값만 남는다.
                            case "jvm.memory.used", "jvm.memory.max" -> id.replaceTags(
                                    List.of(tag(id, "area"), tag(id, "id")));
                            case "hikaricp.connections.active", "hikaricp.connections.max",
                                    "hikaricp.connections.pending", "hikaricp.connections.acquire",
                                    "hikaricp.connections.timeout" -> id.replaceTags(List.of(tag(id, "pool")));
                            default -> id;
                        };
                    }
                });
    }

    private static boolean isAllowed(Meter.Id id) {
        return switch (id.getName()) {
            case "http.server.requests", "setty.http.requests.completed", "setty.http.requests.server.errors",
                    "hikaricp.connections.active", "hikaricp.connections.max",
                    "hikaricp.connections.pending", "hikaricp.connections.acquire",
                    "hikaricp.connections.timeout" -> true;
            case "jvm.memory.used", "jvm.memory.max" -> "heap".equals(id.getTag("area"));
            default -> false;
        };
    }

    private static Tag tag(Meter.Id id, String key) {
        String value = id.getTag(key);
        return Tag.of(key, value == null ? "UNKNOWN" : value);
    }
}
