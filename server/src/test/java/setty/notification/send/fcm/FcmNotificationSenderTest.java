package setty.notification.send.fcm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.firebase.messaging.Message;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import setty.notification.device.application.DeviceTokenService;
import setty.notification.device.domain.DevicePlatform;
import setty.notification.device.domain.DeviceToken;
import setty.notification.owner.NotificationOwner;
import setty.notification.send.NotificationMessage;
import setty.notification.send.SendResult;

@ExtendWith(MockitoExtension.class)
class FcmNotificationSenderTest {

    @Mock
    private DeviceTokenService deviceTokenService;

    @Test
    void 토큰이_없는_사용자는_성공으로_본다() {
        when(deviceTokenService.findAllByOwners(any())).thenReturn(List.of());
        final FcmNotificationSender sender = new FcmNotificationSender(deviceTokenService, messages -> List.of());

        final SendResult result = sender.send(List.of(message(1L, 100L)));

        assertThat(result.failedNotificationIds()).isEmpty();
    }

    @Test
    void 무효_토큰은_삭제하고_그_알림은_실패로_본다() {
        when(deviceTokenService.findAllByOwners(any())).thenReturn(List.of(
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
        when(deviceTokenService.findAllByOwners(any())).thenReturn(List.of(
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
        when(deviceTokenService.findAllByOwners(any())).thenReturn(tokens);
        final List<Integer> batchSizes = new ArrayList<>();
        final FcmNotificationSender sender = new FcmNotificationSender(deviceTokenService, batch -> {
            batchSizes.add(batch.size());
            return batch.stream().map(message -> FcmDelivery.success()).toList();
        });

        sender.send(messages);

        assertThat(batchSizes).containsExactly(500, 500, 200);
    }

    @Test
    void 같은_번호의_회원과_기사는_각자의_기기로만_받는다() {
        when(deviceTokenService.findAllByOwners(any())).thenReturn(List.of(
                token(100L, "member-phone"),
                new DeviceToken(NotificationOwner.driver(100L), "driver-phone", DevicePlatform.WEB)
        ));
        final List<Message> sent = new ArrayList<>();
        final FcmNotificationSender sender = new FcmNotificationSender(deviceTokenService, batch -> {
            sent.addAll(batch);
            return batch.stream().map(message -> FcmDelivery.success()).toList();
        });
        final NotificationMessage driverMessage = NotificationMessage.order(2L, NotificationOwner.driver(100L), null,
                new NotificationMessage.Order(30L, "DRIVER_SETTLEMENT_CONFIRMED", "DRIVER", "배송비 정산이 확정됐어요", "15,000원"));

        sender.send(List.of(message(1L, 100L), driverMessage));

        assertThat(sent).extracting(FcmNotificationSenderTest::tokenOf).containsExactly("member-phone", "driver-phone");
        final Map<String, String> driverData = dataOf(sent.get(1));
        assertThat(driverData).containsEntry("type", "DRIVER")
                .containsEntry("notificationId", "2")
                .containsEntry("orderId", "30")
                .containsEntry("headline", "배송비 정산이 확정됐어요")
                .containsEntry("body", "15,000원")
                .doesNotContainKey("listingId");
    }

    private static NotificationMessage message(final Long notificationId, final Long memberId) {
        return new NotificationMessage(notificationId, memberId, 7L, "s3-notify desk", 150_000, 10_000, NotificationMessage.Priority.HIGH);
    }

    private static DeviceToken token(final Long memberId, final String token) {
        return new DeviceToken(NotificationOwner.member(memberId), token, DevicePlatform.ANDROID);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> dataOf(final Message message) {
        try {
            final var field = Message.class.getDeclaredField("data");
            field.setAccessible(true);
            return (Map<String, String>) field.get(message);
        } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
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
