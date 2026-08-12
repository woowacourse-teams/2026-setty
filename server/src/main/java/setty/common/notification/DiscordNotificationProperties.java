package setty.common.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 알림 종류마다 디스코드 채널이 다르고, 채널마다 웹훅 URL이 다르다.
 */
@ConfigurationProperties(prefix = "setty.notification.discord")
public record DiscordNotificationProperties(
        String dispatchWebhookUrl,
        String estimateWebhookUrl
) {
}
