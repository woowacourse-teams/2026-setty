package setty.common.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "setty.notification.discord")
public record DiscordNotificationProperties(
        String webhookUrl
) {
    public boolean isEnabled() {
        return webhookUrl != null && !webhookUrl.isBlank();
    }
}
