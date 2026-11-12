package setty.aisupport;

import lombok.RequiredArgsConstructor;

import static setty.aisupport.SupportDtos.*;

import org.springframework.jdbc.core.JdbcTemplate;

import setty.common.MemberWithdrawn;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Retention and withdrawal cleanup, invoked by the existing scheduled/listener entry points. */
@RequiredArgsConstructor
final class SupportRetention {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    void withdrawn(MemberWithdrawn event) {
        jdbc.update("DELETE FROM ai_support_conversation WHERE member_id=?", event.memberId());
        jdbc.update("DELETE FROM ai_support_daily_usage WHERE member_id=?", event.memberId());
    }

    void purgeExpired() {
        // Bounded batches keep retention cleanup from holding large transactions.
        int removed;
        do {
            removed =
                    jdbc.update(
                            "DELETE FROM ai_support_conversation WHERE expires_at<=? LIMIT 200",
                            ts(clock.instant()));
        } while (removed == 200);
        var cutoff = SupportDailyUsage.today(clock).minusDays(40);
        do {
            removed = jdbc.update(
                    "DELETE FROM ai_support_daily_usage WHERE usage_date<=? LIMIT 200", cutoff);
        } while (removed == 200);
    }

    private static LocalDateTime ts(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
