package setty.aisupport;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(SupportConfiguration.Settings.class)
class SupportConfiguration {
    @ConfigurationProperties("setty.ai-support")
    record Settings(
            boolean enabled, String region, String modelId, int maxConcurrent, String provider,
            @DefaultValue("2000") long slotWaitMs) {
        @ConstructorBinding
        Settings {
            if (slotWaitMs < 0) throw new IllegalArgumentException("Slot wait must not be negative");
            provider = provider == null || provider.isBlank() ? "bedrock" : provider;
            if (!provider.equals("bedrock") && !provider.equals("openai"))
                throw new IllegalArgumentException("Unsupported AI support provider");
            region = region == null ? "ap-northeast-2" : region;
            modelId =
                    modelId == null || modelId.isBlank()
                            ? (provider.equals("openai")
                                    ? "gpt-6-luna"
                                    : "global.anthropic.claude-haiku-5-5")
                            : modelId;
            maxConcurrent = maxConcurrent <= 0 ? 4 : maxConcurrent;
        }

        Settings(boolean enabled, String region, String modelId, int maxConcurrent, String provider) {
            this(enabled, region, modelId, maxConcurrent, provider, 2000);
        }

        Settings(boolean enabled, String region, String modelId, int maxConcurrent) {
            this(enabled, region, modelId, maxConcurrent, "bedrock");
        }
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "setty.ai-support.provider", havingValue = "openai")
    OpenAiSupportModel supportOpenAiModel(
            Settings settings, ObjectMapper json, Environment environment) {
        return new OpenAiSupportModel(
                settings, json, environment.getProperty("OPENAI_API_KEY", ""));
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(
            name = "setty.ai-support.provider",
            havingValue = "bedrock",
            matchIfMissing = true)
    BedrockRuntimeClient supportBedrockClient(Settings settings) {
        // Construction does not resolve credentials or invoke a model; disabled mode never calls
        // it.
        return BedrockRuntimeClient.builder()
                .region(Region.of(settings.region()))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .overrideConfiguration(
                        c ->
                                c.retryStrategy(r -> r.maxAttempts(1))
                                        .apiCallTimeout(Duration.ofSeconds(55))
                                        .apiCallAttemptTimeout(Duration.ofSeconds(50)))
                .build();
    }
}
