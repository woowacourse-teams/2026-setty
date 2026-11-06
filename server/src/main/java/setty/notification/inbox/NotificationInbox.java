package setty.notification.inbox;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

// 키워드 알림과 주문 알림을 받은 시각순으로 섞어 한 페이지를 자른다. 같은 시각이면 종류·id 순으로 고정해 페이지 경계가 흔들리지 않는다.
@Service
public class NotificationInbox {

    private final JdbcTemplate jdbcTemplate;

    public NotificationInbox(final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Entry> findPage(final Long memberId, final int page, final int size) {
        return jdbcTemplate.query(
                """
                SELECT type, id FROM (
                    SELECT 'KEYWORD' AS type, id, created_at FROM listing_notifications WHERE member_id = ?
                    UNION ALL
                    SELECT 'ORDER' AS type, id, created_at FROM order_notifications WHERE member_id = ?
                ) notifications
                ORDER BY created_at DESC, type DESC, id DESC
                LIMIT ? OFFSET ?
                """,
                (resultSet, rowNum) -> new Entry(Type.valueOf(resultSet.getString("type")), resultSet.getLong("id")),
                memberId, memberId, size, (long) page * size
        );
    }

    public enum Type {
        KEYWORD,
        ORDER
    }

    public record Entry(Type type, Long id) {
    }
}
