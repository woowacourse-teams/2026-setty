package setty.notification.send;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "setty.notification.sender", havingValue = "mock")
public class MockPushNotificationSender implements NotificationSender {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final ObjectMapper objectMapper;
    private final URI sendUri;

    public MockPushNotificationSender(final ObjectMapper objectMapper, final NotificationSendProperties properties) {
        this.objectMapper = objectMapper;
        this.sendUri = URI.create(properties.mockPushUrl() + "/send");
    }

    @Override
    public SendResult send(final List<NotificationMessage> messages) {
        try {
            final HttpRequest request = HttpRequest.newBuilder(sendUri)
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(new SendRequest(messages))))
                    .build();
            final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return SendResult.allFailed(messages);
            }
            return new SendResult(objectMapper.readValue(response.body(), SendResponse.class).failedNotificationIds());
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return SendResult.allFailed(messages);
        } catch (final Exception failed) {
            return SendResult.allFailed(messages);
        }
    }

    record SendRequest(List<NotificationMessage> messages) {
    }

    record SendResponse(Set<Long> failedNotificationIds) {
    }
}
