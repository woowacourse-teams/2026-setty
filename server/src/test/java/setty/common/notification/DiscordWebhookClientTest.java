package setty.common.notification;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

import java.net.SocketTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("디스코드 웹훅 클라이언트")
class DiscordWebhookClientTest {
    private static final String WEBHOOK_URL = "https://webhook.invalid/setty-test";
    private static final String MESSAGE = "새 배차 요청이 접수됐어요";

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    @DisplayName("웹훅 URL이 비어 있으면 아무 요청도 보내지 않는다")
    void sendsNothingWhenWebhookUrlIsBlank() {
        client("").send(MESSAGE);

        server.verify();
    }

    @Test
    @DisplayName("웹훅 URL이 있으면 content 필드에 메시지를 담아 POST한다")
    void postsMessageAsContentField() {
        server.expect(requestTo(WEBHOOK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content").value(MESSAGE))
                .andRespond(withNoContent());

        client(WEBHOOK_URL).send(MESSAGE);

        server.verify();
    }

    @Test
    @DisplayName("디스코드가 오류를 응답해도 예외를 밖으로 전파하지 않는다")
    void swallowsErrorResponse() {
        server.expect(requestTo(WEBHOOK_URL)).andRespond(withServerError());

        assertThatCode(() -> client(WEBHOOK_URL).send(MESSAGE)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("디스코드 연결이 끊기거나 지연돼도 예외를 밖으로 전파하지 않는다")
    void swallowsConnectionFailure() {
        server.expect(requestTo(WEBHOOK_URL)).andRespond(withException(new SocketTimeoutException()));

        assertThatCode(() -> client(WEBHOOK_URL).send(MESSAGE)).doesNotThrowAnyException();
    }

    private DiscordWebhookClient client(final String webhookUrl) {
        return new DiscordWebhookClient(
                builder.build(),
                new DiscordNotificationProperties(webhookUrl),
                JsonMapper.builder().build()
        );
    }
}
