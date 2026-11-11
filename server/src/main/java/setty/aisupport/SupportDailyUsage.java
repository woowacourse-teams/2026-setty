package setty.aisupport;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/** Count accepted text turns in the caller's transaction, independently of chat lifetime. */
final class SupportDailyUsage {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final int limit;

    SupportDailyUsage(JdbcTemplate jdbc, Clock clock, int limit) {
        if (limit < 0) throw new IllegalArgumentException("Daily text limit must not be negative");
        this.jdbc = jdbc;
        this.clock = clock;
        this.limit = limit;
    }

    void acceptText(long member) {
        LocalDate date = today(clock);
        // The upsert locks even an existing row until accept commits. Different conversations
        // share this member/day row; a failed acceptance rolls back both the count and the turn.
        jdbc.update(
                "INSERT INTO ai_support_daily_usage(member_id,usage_date,text_turns) VALUES(?,?,0)"
                        + " ON DUPLICATE KEY UPDATE text_turns=text_turns",
                member, date);
        int changed = jdbc.update(
                "UPDATE ai_support_daily_usage SET text_turns=text_turns+1"
                        + " WHERE member_id=? AND usage_date=? AND (?=0 OR text_turns<?)",
                member, date, limit, limit);
        if (changed == 0)
            throw new SupportException(
                    429,
                    "AI_DAILY_LIMIT",
                    "오늘 상담 한도를 모두 사용했어요. 버튼 선택과 이전 대화 기록은 계속 이용할 수 있어요.");
    }

    static LocalDate today(Clock clock) {
        return clock.instant().atZone(KST).toLocalDate();
    }
}
