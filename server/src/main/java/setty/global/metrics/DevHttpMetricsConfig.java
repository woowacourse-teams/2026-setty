package setty.global.metrics;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import java.util.List;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("dev")
public class DevHttpMetricsConfig {

    @Bean
    MeterRegistryCustomizer<OtlpMeterRegistry> devHttpMetricsOnly() {
        return registry -> registry.config()
                .meterFilter(MeterFilter.denyUnless(id -> id.getName().equals("http.server.requests")))
                .meterFilter(new MeterFilter() {
                    @Override
                    public Meter.Id map(Meter.Id id) {
                        if (!id.getName().equals("http.server.requests")) {
                            return id;
                        }
                        String outcome = id.getTag("outcome");
                        return id.replaceTags(List.of(Tag.of("outcome", outcome == null ? "UNKNOWN" : outcome)));
                    }
                });
    }
}
