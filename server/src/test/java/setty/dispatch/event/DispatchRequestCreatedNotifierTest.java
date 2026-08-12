package setty.dispatch.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import setty.common.notification.DiscordWebhookClient;
import setty.common.web.FrontProperties;
import setty.dispatch.service.OperatorDispatchUrlFactory;

@ExtendWith(MockitoExtension.class)
@DisplayName("배차 요청 접수 알림")
class DispatchRequestCreatedNotifierTest {
    private static final String FRONT_BASE_URL = "https://setty.test";

    @Mock
    private DiscordWebhookClient discordWebhookClient;

    @Captor
    private ArgumentCaptor<String> messageCaptor;

    @Test
    @DisplayName("요청 번호·물품·접수 시각·운영자 화면 링크를 담아 보낸다")
    void sendsRequestSummaryWithOperatorLink() {
        final DispatchRequestCreatedNotifier notifier = notifier();

        notifier.notifyCreated(new DispatchRequestCreatedEvent(
                12L,
                "원목 의자",
                true,
                2,
                "https://www.daangn.com/articles/test-1",
                LocalDateTime.of(2026, 8, 11, 14, 3)
        ));

        verify(discordWebhookClient).send(messageCaptor.capture());
        assertThat(messageCaptor.getValue())
                .contains("#12")
                .contains("원목 의자")
                .contains("50만 원 초과")
                .contains("2장")
                .contains("2026-08-11 14:03")
                .contains("https://www.daangn.com/articles/test-1")
                .contains(FRONT_BASE_URL + "/operator/dispatch-requests/12");
    }

    @Test
    @DisplayName("고가품이 아니고 사진과 게시물 링크가 없으면 그대로 표시한다")
    void sendsSummaryWithoutOptionalValues() {
        final DispatchRequestCreatedNotifier notifier = notifier();

        notifier.notifyCreated(new DispatchRequestCreatedEvent(
                7L,
                "책상",
                false,
                0,
                null,
                LocalDateTime.of(2026, 8, 11, 9, 0)
        ));

        verify(discordWebhookClient).send(messageCaptor.capture());
        assertThat(messageCaptor.getValue())
                .contains("#7")
                .contains("책상")
                .doesNotContain("50만 원 초과")
                .contains("물품 사진: 없음")
                .contains("게시물: 없음");
    }

    private DispatchRequestCreatedNotifier notifier() {
        return new DispatchRequestCreatedNotifier(
                discordWebhookClient,
                new OperatorDispatchUrlFactory(new FrontProperties(FRONT_BASE_URL))
        );
    }
}
