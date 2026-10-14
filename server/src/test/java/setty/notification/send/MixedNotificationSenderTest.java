package setty.notification.send;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import setty.notification.device.application.DeviceTokenService;
import setty.notification.device.domain.DevicePlatform;
import setty.notification.device.domain.DeviceToken;

@ExtendWith(MockitoExtension.class)
class MixedNotificationSenderTest {

    @Mock
    private DeviceTokenService deviceTokenService;

    @Test
    void 폰_토큰이_있는_회원은_실기기로_없는_회원은_모의_서버로_보낸다() {
        when(deviceTokenService.findAllByMemberIds(any())).thenReturn(List.of(token(100L)));
        final RecordingSender device = new RecordingSender(Set.of());
        final RecordingSender load = new RecordingSender(Set.of());

        new MixedNotificationSender(deviceTokenService, device, load)
                .send(List.of(message(1L, 100L), message(2L, 200L), message(3L, 300L)));

        assertThat(device.sentIds()).containsExactly(1L);
        assertThat(load.sentIds()).containsExactly(2L, 3L);
    }

    @Test
    void 모의_서버로_먼저_보내고_실기기로_보낸다() {
        when(deviceTokenService.findAllByMemberIds(any())).thenReturn(List.of(token(100L)));
        final List<String> order = new ArrayList<>();
        final NotificationSender device = messages -> {
            order.add("device");
            return SendResult.allSucceeded();
        };
        final NotificationSender load = messages -> {
            order.add("load");
            return SendResult.allSucceeded();
        };

        new MixedNotificationSender(deviceTokenService, device, load)
                .send(List.of(message(1L, 100L), message(2L, 200L)));

        assertThat(order).containsExactly("load", "device");
    }

    @Test
    void 양쪽의_실패를_합친다() {
        when(deviceTokenService.findAllByMemberIds(any())).thenReturn(List.of(token(100L)));
        final RecordingSender device = new RecordingSender(Set.of(1L));
        final RecordingSender load = new RecordingSender(Set.of(3L));

        final SendResult result = new MixedNotificationSender(deviceTokenService, device, load)
                .send(List.of(message(1L, 100L), message(2L, 200L), message(3L, 300L)));

        assertThat(result.failedNotificationIds()).containsExactlyInAnyOrder(1L, 3L);
    }

    @Test
    void 보낼_대상이_없는_쪽은_부르지_않는다() {
        when(deviceTokenService.findAllByMemberIds(any())).thenReturn(List.of());
        final RecordingSender device = new RecordingSender(Set.of());
        final RecordingSender load = new RecordingSender(Set.of());

        new MixedNotificationSender(deviceTokenService, device, load).send(List.of(message(2L, 200L)));

        assertThat(device.calls()).isZero();
        assertThat(load.calls()).isOne();
    }

    private static NotificationMessage message(final Long notificationId, final Long memberId) {
        return new NotificationMessage(notificationId, memberId, 7L, "s3-notify desk", NotificationMessage.Priority.HIGH);
    }

    private static DeviceToken token(final Long memberId) {
        return new DeviceToken(memberId, "token-" + memberId, DevicePlatform.ANDROID);
    }

    private static final class RecordingSender implements NotificationSender {

        private final Set<Long> failing;
        private final List<Long> sentIds = new ArrayList<>();
        private int calls;

        private RecordingSender(final Set<Long> failing) {
            this.failing = failing;
        }

        @Override
        public SendResult send(final List<NotificationMessage> messages) {
            calls++;
            messages.forEach(message -> sentIds.add(message.notificationId()));
            return new SendResult(messages.stream()
                    .map(NotificationMessage::notificationId)
                    .filter(failing::contains)
                    .collect(Collectors.toSet()));
        }

        private List<Long> sentIds() {
            return sentIds;
        }

        private int calls() {
            return calls;
        }
    }
}
