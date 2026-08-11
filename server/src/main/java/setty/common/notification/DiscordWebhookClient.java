package setty.common.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

@Component
public class DiscordWebhookClient {
    private static final Logger log = LoggerFactory.getLogger(DiscordWebhookClient.class);

    private final RestClient discordRestClient;
    private final DiscordNotificationProperties discordNotificationProperties;
    private final ObjectMapper objectMapper;

    public DiscordWebhookClient(
            final RestClient discordRestClient,
            final DiscordNotificationProperties discordNotificationProperties,
            final ObjectMapper objectMapper
    ) {
        this.discordRestClient = discordRestClient;
        this.discordNotificationProperties = discordNotificationProperties;
        this.objectMapper = objectMapper;
    }

    public void send(final String content) {
        if (!discordNotificationProperties.isEnabled()) {
            log.debug("디스코드 웹훅 URL이 설정되지 않아 알림을 보내지 않는다.");
            return;
        }
        try {
            // 본문을 미리 직렬화해 Content-Length가 붙게 한다. 스트리밍으로 보내면 chunked 요청이 된다.
            final byte[] payload = objectMapper.writeValueAsBytes(new DiscordMessage(content));

            discordRestClient.post()
                    .uri(discordNotificationProperties.webhookUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (final RestClientResponseException e) {
            log.warn("디스코드가 알림을 거부했다. status={}", e.getStatusCode().value());
        } catch (final RuntimeException e) {
            log.warn("디스코드 알림 발송에 실패했다. reason={}", e.getClass().getSimpleName());
        }
    }
}
