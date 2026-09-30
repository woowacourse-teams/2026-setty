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

class HttpMetricsAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(MetricsAutoConfiguration.class,
                    CompositeMeterRegistryAutoConfiguration.class, SimpleMetricsExportAutoConfiguration.class,
                    OtlpMetricsExportAutoConfiguration.class))
            .withUserConfiguration(MetricsConfig.class)
            .withBean(OtlpMetricsSender.class, () -> mock(OtlpMetricsSender.class));

    @ParameterizedTest
    @ValueSource(strings = {"dev", "prod"})
    void enabledProfilesCreateOtlpRegistryWithTheirOwnEnvironment(String profile) {
        runner.withPropertyValues("spring.profiles.active=" + profile, "SETTY_METRICS_ENABLED=true").run(context -> {
            assertThat(context).hasSingleBean(OtlpMeterRegistry.class);
            assertThat(context).hasSingleBean(HttpRequestCountHandler.class);
            var registry = context.getBean(OtlpMeterRegistry.class);
            assertThat(registry.get("setty.http.requests.completed").counter().count()).isZero();
            assertThat(registry.get("setty.http.requests.server.errors").counter().count()).isZero();
            OtlpConfig config = context.getBean(OtlpConfig.class);
            assertThat(config.url()).isEqualTo("http://127.0.0.1:4318/v1/metrics");
            assertThat(config.step()).isEqualTo(Duration.ofMinutes(1));
            assertThat(config.baseTimeUnit()).isEqualTo(TimeUnit.MILLISECONDS);
            assertThat(config.aggregationTemporality()).isEqualTo(AggregationTemporality.CUMULATIVE);
            assertThat(config.histogramFlavor()).isEqualTo(HistogramFlavor.BASE2_EXPONENTIAL_BUCKET_HISTOGRAM);
            assertThat(config.publishMaxGaugeForHistograms()).isFalse();
            assertThat(config.resourceAttributes())
                    .containsEntry("service.name", "setty-backend")
                    .containsEntry("deployment.environment.name", profile);
            assertThat(context.getEnvironment().getProperty(
                    "management.metrics.distribution.percentiles-histogram.http.server.requests"))
                    .isEqualTo("true");
            assertThat(context.getEnvironment().getProperty(
                    "management.metrics.distribution.percentiles-histogram.hikaricp.connections.acquire"))
                    .isEqualTo("true");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"default", "local", "prod"})
    void defaultLocalAndProdDoNotExportWithoutOptIn(String profile) {
        runner.withPropertyValues("spring.profiles.active=" + profile).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(OtlpMeterRegistry.class);
            assertThat(context).doesNotHaveBean(MetricsConfig.class);
            assertThat(context).doesNotHaveBean(HttpRequestCountHandler.class);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "prod"})
    void disablingExportAlsoDisablesCustomInstrumentation(String profile) {
        runner.withPropertyValues("spring.profiles.active=" + profile,
                "management.otlp.metrics.export.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(OtlpMeterRegistry.class);
            assertThat(context).doesNotHaveBean(MetricsConfig.class);
            assertThat(context).doesNotHaveBean(HttpRequestCountHandler.class);
        });
    }

    @Test
    void prodSwitchDoesNotTurnOffExistingDevExport() {
        runner.withPropertyValues("spring.profiles.active=dev", "SETTY_METRICS_ENABLED=false").run(context -> {
            assertThat(context).hasSingleBean(OtlpMeterRegistry.class);
            assertThat(context).hasSingleBean(HttpRequestCountHandler.class);
        });
    }
}
