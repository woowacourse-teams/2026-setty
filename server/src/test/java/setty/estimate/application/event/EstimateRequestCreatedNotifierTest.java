package setty.estimate.application.event;

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
import setty.estimate.application.OperatorEstimateUrlFactory;

@ExtendWith(MockitoExtension.class)
@DisplayName("견적 요청 접수 알림")
class EstimateRequestCreatedNotifierTest {
    private static final String FRONT_BASE_URL = "https://setty.test";
    private static final String DISPATCH_WEBHOOK_URL = "https://webhook.invalid/setty-dispatch";
    private static final String ESTIMATE_WEBHOOK_URL = "https://webhook.invalid/setty-estimate";

    @Mock
    private DiscordWebhookClient discordWebhookClient;

    @Captor
    private ArgumentCaptor<String> messageCaptor;

    @Test
    @DisplayName("요청 번호·물품·접수 시각·운영자 화면 링크를 담아 보낸다")
    void sendsRequestSummaryWithOperatorLink() {
        notifier().notifyCreated(new EstimateRequestCreatedEvent(
                31L,
                "원목의자",
                true,
                "https://www.daangn.com/articles/test-1",
                LocalDateTime.of(2026, 8, 12, 10, 30)
        ));

        verify(discordWebhookClient).send(eq(ESTIMATE_WEBHOOK_URL), messageCaptor.capture());
        assertThat(messageCaptor.getValue())
                .contains("#31")
                .contains("원목의자")
                .contains("50만 원 초과")
                .contains("2026-08-12 10:30")
                .contains("https://www.daangn.com/articles/test-1")
                .contains(FRONT_BASE_URL + "/operator/estimate-requests/31");
    }

    @Test
    @DisplayName("고가품이 아니고 게시물 링크가 없으면 그대로 표시한다")
    void sendsSummaryWithoutOptionalValues() {
        notifier().notifyCreated(new EstimateRequestCreatedEvent(
                8L,
                "책상",
                false,
                null,
                LocalDateTime.of(2026, 8, 12, 9, 0)
        ));

        verify(discordWebhookClient).send(eq(ESTIMATE_WEBHOOK_URL), messageCaptor.capture());
        assertThat(messageCaptor.getValue())
                .contains("#8")
                .contains("책상")
                .doesNotContain("50만 원 초과")
                .contains("게시물: 없음");
    }

    private EstimateRequestCreatedNotifier notifier() {
        return new EstimateRequestCreatedNotifier(
                discordWebhookClient,
                new DiscordNotificationProperties(DISPATCH_WEBHOOK_URL, ESTIMATE_WEBHOOK_URL),
                new OperatorEstimateUrlFactory(new FrontProperties(FRONT_BASE_URL))
        );
    }
}
