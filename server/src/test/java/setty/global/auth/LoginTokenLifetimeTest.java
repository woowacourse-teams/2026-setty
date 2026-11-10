package setty.global.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginTokenLifetimeTest {

    private static final Instant NOW = Instant.parse("2026-11-11T00:00:00Z");
    private static final Duration TTL = Duration.ofDays(30);

    private final LoginTokenLifetime lifetime = new LoginTokenLifetime(
            new LoginTokenProperties(TTL), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void 만료_시각은_지금부터_수명만큼_뒤다() {
        assertThat(lifetime.expiresAt()).isEqualTo(NOW.plus(TTL));
    }

    @Test
    void 만료_시각_전이면_유효하다() {
        assertThat(lifetime.isExpired(NOW.plusNanos(1))).isFalse();
    }

    @Test
    void 만료_시각이_되면_만료다() {
        assertThat(lifetime.isExpired(NOW)).isTrue();
        assertThat(lifetime.isExpired(NOW.minusNanos(1))).isTrue();
    }

    @Test
    void 만료_시각이_없으면_만료다() {
        assertThat(lifetime.isExpired(null)).isTrue();
    }
}
