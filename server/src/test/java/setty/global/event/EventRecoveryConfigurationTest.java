package setty.global.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.EventPublication.Status;
import org.springframework.modulith.events.config.StalenessProperties;

class EventRecoveryConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(StalenessBinding.class);

    @ParameterizedTest
    @ValueSource(strings = {"default", "dev", "prod"})
    void 모든_환경에서_시작_전체_재발행_대신_중단_상태_감시를_사용한다(final String profile) {
        runner.withPropertyValues("spring.profiles.active=" + profile).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getProperty(
                    "spring.modulith.events.republish-outstanding-events-on-restart", Boolean.class)).isFalse();
            final StalenessProperties properties = context.getBean(StalenessProperties.class);
            for (final Status status : new Status[]{Status.PUBLISHED, Status.PROCESSING, Status.RESUBMITTED}) {
                assertThat(properties.isMonitored(status)).isTrue();
                assertThat(properties.getStaleness(status)).isPositive();
            }
        });
    }

    @Test
    void 환경변수로_상태별_제한시간과_복구_주기를_조정한다() {
        runner.withPropertyValues(
                "SETTY_EVENT_STALENESS_PUBLISHED=PT7M",
                "SETTY_EVENT_STALENESS_PROCESSING=PT9M",
                "SETTY_EVENT_STALENESS_RESUBMITTED=PT8M",
                "SETTY_EVENT_STALENESS_CHECK_INTERVAL=PT15S",
                "SETTY_EVENT_RESUBMISSION_INTERVAL=PT20S"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            final StalenessProperties properties = context.getBean(StalenessProperties.class);
            assertThat(properties.getStaleness(Status.PUBLISHED)).isEqualTo(Duration.ofMinutes(7));
            assertThat(properties.getStaleness(Status.PROCESSING)).isEqualTo(Duration.ofMinutes(9));
            assertThat(properties.getStaleness(Status.RESUBMITTED)).isEqualTo(Duration.ofMinutes(8));
            assertThat(Binder.get(context.getEnvironment()).bind(
                    "spring.modulith.events.staleness.check-interval", Duration.class).get())
                    .isEqualTo(Duration.ofSeconds(15));
            assertThat(Binder.get(context.getEnvironment()).bind(
                    "setty.events.resubmission-interval", Duration.class).get())
                    .isEqualTo(Duration.ofSeconds(20));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT0S", "-PT1S"})
    void 명시한_제한시간이_양수가_아니면_기동에_실패한다(final String timeout) {
        runner.withPropertyValues("SETTY_EVENT_STALENESS_PROCESSING=" + timeout).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class)
                    .hasStackTraceContaining("must be greater than zero");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(StalenessProperties.class)
    static class StalenessBinding {
    }
}
