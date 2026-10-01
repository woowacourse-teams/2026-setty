package setty.global.event;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 모듈 간 이벤트는 발행한 쪽이 커밋된 뒤 비동기로 처리되므로, 테스트는 처리가 끝날 때까지 기다린 뒤 결과를 확인한다.
 */
public final class EventPublicationTestSupport {

    private EventPublicationTestSupport() {
    }

    // 모든 발행 기록이 완료되어 지워졌거나 FAILED로 남을 때까지 기다린다.
    public static void awaitEventsHandled(final JdbcTemplate jdbcTemplate) {
        await().atMost(Duration.ofSeconds(10)).until(() -> jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM EVENT_PUBLICATION WHERE STATUS IN ('PUBLISHED', 'PROCESSING', 'RESUBMITTED')",
                Long.class
        ) == 0L);
    }

    // 완료된 발행 기록은 지워지므로 남은 행은 재발행 대상인 미완료 건이다.
    public static long incompletePublicationCount(final JdbcTemplate jdbcTemplate, final Class<?> eventType) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM EVENT_PUBLICATION WHERE EVENT_TYPE = ? AND COMPLETION_DATE IS NULL",
                Long.class,
                eventType.getName()
        );
    }
}
