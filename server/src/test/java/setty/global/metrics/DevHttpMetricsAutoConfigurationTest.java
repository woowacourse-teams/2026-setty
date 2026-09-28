package setty.global.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.micrometer.registry.otlp.AggregationTemporality;
import io.micrometer.registry.otlp.HistogramFlavor;
import io.micrometer.registry.otlp.OtlpConfig;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import io.micrometer.registry.otlp.OtlpMetricsSender;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.otlp.OtlpMetricsExportAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DevHttpMetricsAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(MetricsAutoConfiguration.class,
                    CompositeMeterRegistryAutoConfiguration.class, SimpleMetricsExportAutoConfiguration.class,
                    OtlpMetricsExportAutoConfiguration.class))
            .withUserConfiguration(DevMetricsConfig.class)
            .withBean(OtlpMetricsSender.class, () -> mock(OtlpMetricsSender.class));

    @Test
    void devCreatesOtlpRegistryWithApplicationConfiguration() {
        runner.withPropertyValues("spring.profiles.active=dev").run(context -> {
            assertThat(context).hasSingleBean(OtlpMeterRegistry.class);
            OtlpConfig config = context.getBean(OtlpConfig.class);
            assertThat(config.url()).isEqualTo("http://127.0.0.1:4318/v1/metrics");
            assertThat(config.step()).isEqualTo(Duration.ofMinutes(1));
            assertThat(config.baseTimeUnit()).isEqualTo(TimeUnit.MILLISECONDS);
            assertThat(config.aggregationTemporality()).isEqualTo(AggregationTemporality.CUMULATIVE);
            assertThat(config.histogramFlavor()).isEqualTo(HistogramFlavor.BASE2_EXPONENTIAL_BUCKET_HISTOGRAM);
            assertThat(config.publishMaxGaugeForHistograms()).isFalse();
            assertThat(config.resourceAttributes())
                    .containsEntry("service.name", "setty-backend")
                    .containsEntry("deployment.environment.name", "dev");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"default", "local", "prod"})
    void nonDevProfilesDoNotCreateOtlpRegistry(String profile) {
        runner.withPropertyValues("spring.profiles.active=" + profile).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(OtlpMeterRegistry.class);
            assertThat(context).doesNotHaveBean(DevMetricsConfig.class);
        });
    }
}
