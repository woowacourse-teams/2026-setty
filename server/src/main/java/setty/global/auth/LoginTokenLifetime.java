package setty.global.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Component;

@NamedInterface("auth")
@Component
@EnableConfigurationProperties(LoginTokenProperties.class)
public class LoginTokenLifetime {

    private final Duration ttl;
    private final Clock clock;

    public LoginTokenLifetime(final LoginTokenProperties properties, final Clock clock) {
        this.ttl = properties.tokenTtl();
        this.clock = clock;
    }

    public Instant expiresAt() {
        return clock.instant().plus(ttl);
    }

    public boolean isExpired(final Instant expiresAt) {
        return expiresAt == null || !clock.instant().isBefore(expiresAt);
    }
}
