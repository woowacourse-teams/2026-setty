package setty.notification.send.fcm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.firebase.messaging.Message;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import setty.notification.device.application.DeviceTokenService;
import setty.notification.device.domain.DevicePlatform;
import setty.notification.device.domain.DeviceToken;
import setty.notification.send.NotificationMessage;
import setty.notification.send.SendResult;

@ExtendWith(MockitoExtension.class)
class FcmNotificationSenderTest {

    @Mock
    private DeviceTokenService deviceTokenService;

    @Test
    void 토큰이_없는_사용자는_성공으로_본다() {
        when(deviceTokenService.findAllByMemberIds(any())).thenReturn(List.of());
        final FcmNotificationSender sender = new FcmNotificationSender(deviceTokenService, messages -> List.of());

        final SendResult result = sender.send(List.of(message(1L, 100L)));

        assertThat(result.failedNotificationIds()).isEmpty();
    }

    @Test
    void 무효_토큰은_삭제하고_그_알림은_실패로_본다() {
        when(deviceTokenService.findAllByMemberIds(any())).thenReturn(List.of(
                token(100L, "valid-token"),
                token(200L, "dead-token")
        ));
        final FcmNotificationSender sender = new FcmNotificationSender(deviceTokenService, messages ->
                messages.stream().map(message -> tokenOf(message).equals("dead-token")
                        ? FcmDelivery.invalidToken()
                        : FcmDelivery.success()).toList());

        final SendResult result = sender.send(List.of(message(1L, 100L), message(2L, 200L)));

        assertThat(result.failedNotificationIds()).containsExactly(2L);
        verify(deviceTokenService).removeInvalid(Set.of("dead-token"));
    }

    @Test
    void 기기가_여러_대면_하나라도_성공하면_성공이다() {
        when(deviceTokenService.findAllByMemberIds(any())).thenReturn(List.of(
                token(100L, "phone"),
                token(100L, "tablet")
        ));
        final FcmNotificationSender sender = new FcmNotificationSender(deviceTokenService, messages ->
                messages.stream().map(message -> tokenOf(message).equals("tablet")
                        ? FcmDelivery.failure()
                        : FcmDelivery.success()).toList());

        final SendResult result = sender.send(List.of(message(1L, 100L)));

        assertThat(result.failedNotificationIds()).isEmpty();
    }

    @Test
    void 오백_건을_넘으면_나눠서_보낸다() {
        final List<DeviceToken> tokens = new ArrayList<>();
        final List<NotificationMessage> messages = new ArrayList<>();
        for (long i = 1; i <= 1_200; i++) {
            tokens.add(token(i, "token-" + i));
            messages.add(message(i, i));
        }
        when(deviceTokenService.findAllByMemberIds(any())).thenReturn(tokens);
        final List<Integer> batchSizes = new ArrayList<>();
        final FcmNotificationSender sender = new FcmNotificationSender(deviceTokenService, batch -> {
            batchSizes.add(batch.size());
            return batch.stream().map(message -> FcmDelivery.success()).toList();
        });

        sender.send(messages);

        assertThat(batchSizes).containsExactly(500, 500, 200);
    }

    private static NotificationMessage message(final Long notificationId, final Long memberId) {
        return new NotificationMessage(notificationId, memberId, 7L, "s3-notify desk", NotificationMessage.Priority.HIGH);
    }

    private static DeviceToken token(final Long memberId, final String token) {
        return new DeviceToken(memberId, token, DevicePlatform.ANDROID);
    }

    private static String tokenOf(final Message message) {
        try {
            final var field = Message.class.getDeclaredField("token");
            field.setAccessible(true);
            return (String) field.get(message);
        } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
