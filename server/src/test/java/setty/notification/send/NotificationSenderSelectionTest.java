package setty.notification.send;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.LazyInitializationBeanFactoryPostProcessor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import setty.notification.device.application.DeviceTokenService;
import setty.notification.send.fcm.FcmClient;
import setty.notification.send.fcm.FcmConfiguration;
import setty.notification.send.fcm.FcmNotificationSender;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class NotificationSenderSelectionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(context -> context.addBeanFactoryPostProcessor(new LazyInitializationBeanFactoryPostProcessor()))
            .withUserConfiguration(
                    NoopNotificationSender.class,
                    MockPushNotificationSender.class,
                    FcmNotificationSender.class,
                    FcmConfiguration.class,
                    MixedSendConfiguration.class)
            .withBean(DeviceTokenService.class, () -> mock(DeviceTokenService.class))
            .withBean(ObjectMapper.class, () -> JsonMapper.builder().build())
            .withBean(NotificationSendProperties.class, () -> new NotificationSendProperties(4, null, null));

    @ParameterizedTest
    @CsvSource({
            "noop, setty.notification.send.NoopNotificationSender",
            "mock, setty.notification.send.MockPushNotificationSender",
            "fcm, setty.notification.send.fcm.FcmNotificationSender",
            "mixed, setty.notification.send.MixedNotificationSender"
    })
    void 설정값마다_발송기는_하나만_만들어진다(final String sender, final Class<?> expected) {
        runner.withPropertyValues("setty.notification.sender=" + sender).run(context -> {
            final String[] senders = context.getBeanNamesForType(NotificationSender.class);
            assertThat(senders).hasSize(1);
            assertThat(context.getType(senders[0])).isEqualTo(expected);
        });
    }

    @Test
    void 설정값이_없으면_보내지_않는다() {
        runner.run(context -> {
            final String[] senders = context.getBeanNamesForType(NotificationSender.class);
            assertThat(senders).hasSize(1);
            assertThat(context.getType(senders[0])).isEqualTo(NoopNotificationSender.class);
        });
    }

    @ParameterizedTest
    @CsvSource({"fcm, 1", "mixed, 1", "mock, 0", "noop, 0"})
    void FCM_연결은_fcm과_mixed에서만_만든다(final String sender, final int expectedCount) {
        runner.withPropertyValues("setty.notification.sender=" + sender).run(context ->
                assertThat(context.getBeanNamesForType(FcmClient.class)).hasSize(expectedCount));
    }
}
