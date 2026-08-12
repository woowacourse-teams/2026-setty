package setty.dispatch.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import setty.common.notification.DiscordNotificationProperties;
import setty.common.notification.DiscordWebhookClient;
import setty.common.web.FrontProperties;
import setty.dispatch.service.OperatorDispatchUrlFactory;

@ExtendWith(MockitoExtension.class)
@DisplayName("판매자 입력 완료 알림")
class SellerInputCompletedNotifierTest {
    private static final String FRONT_BASE_URL = "https://setty.test";
    private static final String DISPATCH_WEBHOOK_URL = "https://webhook.invalid/setty-dispatch";
    private static final String ESTIMATE_WEBHOOK_URL = "https://webhook.invalid/setty-estimate";

    @Mock
    private DiscordWebhookClient discordWebhookClient;

    @Captor
    private ArgumentCaptor<String> messageCaptor;

    @Test
    @DisplayName("배차 채널로 요청 번호·물품·두 시각·운영자 화면 링크를 담아 보낸다")
    void sendsSummaryToDispatchChannel() {
        notifier().notifySellerInputCompleted(new SellerInputCompletedEvent(
                12L,
                "원목 의자",
                true,
                2,
                "https://www.daangn.com/articles/test-1",
                LocalDateTime.of(2026, 8, 11, 14, 3),
                LocalDateTime.of(2026, 8, 12, 9, 12)
        ));

        verify(discordWebhookClient).send(eq(DISPATCH_WEBHOOK_URL), messageCaptor.capture());
        assertThat(messageCaptor.getValue())
                .contains("판매자 입력이 끝난")
                .contains("#12")
                .contains("원목 의자")
                .contains("50만 원 초과")
                .contains("2장")
                .contains("구매자 접수: 2026-08-11 14:03")
                .contains("판매자 입력 완료: 2026-08-12 09:12")
                .contains("https://www.daangn.com/articles/test-1")
                .contains(FRONT_BASE_URL + "/operator/dispatch-requests/12");
    }

    @Test
    @DisplayName("고가품이 아니고 사진과 게시물 링크가 없으면 그대로 표시한다")
    void sendsSummaryWithoutOptionalValues() {
        notifier().notifySellerInputCompleted(new SellerInputCompletedEvent(
                7L,
                "책상",
                false,
                0,
                null,
                LocalDateTime.of(2026, 8, 12, 8, 0),
                LocalDateTime.of(2026, 8, 12, 9, 0)
        ));

        verify(discordWebhookClient).send(eq(DISPATCH_WEBHOOK_URL), messageCaptor.capture());
        assertThat(messageCaptor.getValue())
                .contains("#7")
                .contains("책상")
                .doesNotContain("50만 원 초과")
                .contains("물품 사진: 없음")
                .contains("게시물: 없음");
    }

    private SellerInputCompletedNotifier notifier() {
        return new SellerInputCompletedNotifier(
                discordWebhookClient,
                new DiscordNotificationProperties(DISPATCH_WEBHOOK_URL, ESTIMATE_WEBHOOK_URL),
                new OperatorDispatchUrlFactory(new FrontProperties(FRONT_BASE_URL))
        );
    }
}
