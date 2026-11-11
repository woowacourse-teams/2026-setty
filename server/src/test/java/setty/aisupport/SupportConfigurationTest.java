package setty.aisupport;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class SupportConfigurationTest {
    final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SupportConfiguration.class, BedrockSupportModel.class)
            .withBean(ObjectMapper.class, () -> JsonMapper.builder().build());

    @Test void missingProviderPreservesBedrockWithoutCredentialResolution() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(BedrockSupportModel.class)
                    .hasSingleBean(BedrockRuntimeClient.class).doesNotHaveBean(OpenAiSupportModel.class);
        });
    }
    @Test void openAiOffStartsWithoutKeyAndDoesNotConstructBedrockClient() {
        runner.withPropertyValues("setty.ai-support.provider=openai", "setty.ai-support.enabled=false", "OPENAI_API_KEY=")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(OpenAiSupportModel.class)
                            .doesNotHaveBean(BedrockRuntimeClient.class).doesNotHaveBean(BedrockSupportModel.class);
                    assertThat(context.getBean(SupportConfiguration.Settings.class).modelId()).isEqualTo("gpt-6-luna");
                });
    }
    @Test void slotWaitDefaultsToTwoSecondsAndKeepsFourSlots() {
        runner.run(context -> {
            var settings = context.getBean(SupportConfiguration.Settings.class);
            assertThat(settings.slotWaitMs()).isEqualTo(2000);
            assertThat(settings.maxConcurrent()).isEqualTo(4);
        });
    }
    @Test void slotWaitAllowsZeroAndRejectsNegativeValues() {
        runner.withPropertyValues("setty.ai-support.slot-wait-ms=0").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(SupportConfiguration.Settings.class).slotWaitMs()).isZero();
        });
        runner.withPropertyValues("setty.ai-support.slot-wait-ms=-1")
                .run(context -> assertThat(context).hasFailed());
    }
    @Test void slotWaitReadsEnvironmentVariableThroughApplicationConfiguration() {
        runner.withInitializer(context -> {
            try {
                var loader = new org.springframework.boot.env.YamlPropertySourceLoader();
                loader.load("application", new org.springframework.core.io.ClassPathResource("application.yml"))
                        .forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
            } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
            context.getEnvironment().getPropertySources().addFirst(
                    new org.springframework.core.env.SystemEnvironmentPropertySource("slot-test-env",
                            java.util.Map.of("SETTY_AI_SUPPORT_SLOT_WAIT_MS", "37")));
        }).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(SupportConfiguration.Settings.class).slotWaitMs()).isEqualTo(37);
        });
    }
    @Test void unknownProviderAndEnabledOpenAiWithoutKeyFailStartup() {
        runner.withPropertyValues("setty.ai-support.provider=typo").run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("setty.ai-support.provider=openai", "setty.ai-support.enabled=true", "OPENAI_API_KEY=")
                .run(context -> assertThat(context).hasFailed());
    }
}
