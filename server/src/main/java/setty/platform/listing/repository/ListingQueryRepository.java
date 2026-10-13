package setty.platform.listing.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import setty.platform.listing.domain.ConditionGrade;
import setty.platform.listing.domain.ListingCategory;

// 목록은 JPA 엔티티 대신 JDBC로 읽는다. OSIV 아래에서 JPA로 조회하면 응답이 끝날 때까지 커넥션을 붙잡지만,
// 트랜잭션 밖의 JdbcTemplate은 쿼리가 끝나면 바로 반납한다.
@Repository
public class ListingQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    public ListingQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Row> findAvailable(Instant cursorCreatedAt, Long cursorId, Integer limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, title, price, delivery_fee, category, condition_grade,
                       width_cm, depth_cm, height_cm, created_at
                FROM listings
                WHERE deleted_at IS NULL AND sale_status = 'AVAILABLE'
                """);
        List<Object> args = new ArrayList<>();
        if (cursorCreatedAt != null) {
            sql.append(" AND (created_at < ? OR (created_at = ? AND id < ?))");
            Timestamp createdAt = Timestamp.from(cursorCreatedAt);
            args.add(createdAt);
            args.add(createdAt);
            args.add(cursorId);
        }
        sql.append(" ORDER BY created_at DESC, id DESC");
        if (limit != null) {
            sql.append(" LIMIT ?");
            args.add(limit);
        }
        return jdbcTemplate.query(sql.toString(), ListingQueryRepository::mapRow, args.toArray());
    }

    public Map<Long, String> findThumbnailObjectKeys(List<Long> listingIds) {
        String placeholders = String.join(",", Collections.nCopies(listingIds.size(), "?"));
        Map<Long, String> objectKeys = new HashMap<>();
        jdbcTemplate.query(
                "SELECT listing_id, object_key FROM listing_images WHERE display_order = 1 AND listing_id IN ("
                        + placeholders + ")",
                (ResultSet resultSet) -> {
                    objectKeys.put(resultSet.getLong("listing_id"), resultSet.getString("object_key"));
                },
                listingIds.toArray()
        );
        return objectKeys;
    }

    private static Row mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new Row(
                resultSet.getLong("id"),
                resultSet.getString("title"),
                resultSet.getInt("price"),
                resultSet.getInt("delivery_fee"),
                ListingCategory.valueOf(resultSet.getString("category")),
                ConditionGrade.valueOf(resultSet.getString("condition_grade")),
                resultSet.getInt("width_cm"),
                resultSet.getInt("depth_cm"),
                resultSet.getInt("height_cm"),
                resultSet.getTimestamp("created_at").toInstant()
        );
    }

    public record Row(
            Long id,
            String title,
            Integer price,
            Integer deliveryFee,
            ListingCategory category,
            ConditionGrade conditionGrade,
            Integer widthCm,
            Integer depthCm,
            Integer heightCm,
            Instant createdAt
    ) {
    }
}
