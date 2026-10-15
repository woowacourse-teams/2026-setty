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
import java.util.Optional;
import java.util.function.Function;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Repository;
import setty.deliveryfee.VolumeTier;
import setty.platform.listing.domain.ConditionGrade;
import setty.platform.listing.domain.ListingCategory;

// 목록은 JPA 엔티티 대신 JDBC로 읽는다. OSIV 아래에서 JPA로 조회하면 응답이 끝날 때까지 커넥션을 붙잡지만,
// 트랜잭션 밖의 JdbcTemplate은 쿼리가 끝나면 바로 반납한다.
@Repository
public class ListingQueryRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, title, price, delivery_fee, category, condition_grade,
                   width_cm, depth_cm, height_cm, has_purchase_request, created_at, dong_code, volume_tier""";
    private static final String ORDER_BY_LATEST = " ORDER BY created_at DESC, id DESC";

    private final JdbcTemplate jdbcTemplate;

    public ListingQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // 목록 요청의 쿼리 여러 개를 커넥션 한 번 빌려 읽는다. 쿼리마다 빌리고 돌려주면 풀이 붐빌 때 대기가 그만큼 늘어난다.
    // 트랜잭션은 열지 않고, work가 끝나면(응답 직렬화 전) 커넥션을 돌려준다.
    public <T> T inOneConnection(Function<ListingQueryRepository, T> work) {
        return jdbcTemplate.execute((ConnectionCallback<T>) connection -> work.apply(
                new ListingQueryRepository(new JdbcTemplate(new SingleConnectionDataSource(connection, true)))));
    }

    public List<Row> findAvailable(
            Instant cursorCreatedAt,
            Long cursorId,
            Integer limit,
            Map<VolumeTier, List<String>> sellerDongCodes
    ) {
        StringBuilder where = new StringBuilder(" WHERE deleted_at IS NULL AND sale_status = 'AVAILABLE'");
        List<Object> args = new ArrayList<>();
        if (cursorCreatedAt != null) {
            where.append(" AND (created_at < ? OR (created_at = ? AND id < ?))");
            Timestamp createdAt = Timestamp.from(cursorCreatedAt);
            args.add(createdAt);
            args.add(createdAt);
            args.add(cursorId);
        }
        if (sellerDongCodes != null) {
            List<String> conditions = new ArrayList<>();
            sellerDongCodes.forEach((volumeTier, dongCodes) -> {
                conditions.add("(volume_tier = ? AND dong_code IN ("
                        + String.join(",", Collections.nCopies(dongCodes.size(), "?")) + "))");
                args.add(volumeTier.name());
                args.addAll(dongCodes);
            });
            where.append(" AND (").append(String.join(" OR ", conditions)).append(")");
        }
        String page = ORDER_BY_LATEST;
        if (limit != null) {
            page += " LIMIT ?";
            args.add(limit);
        }
        if (sellerDongCodes == null) {
            return jdbcTemplate.query(SELECT_COLUMNS + " FROM listings" + where + page,
                    ListingQueryRepository::mapRow, args.toArray());
        }
        // id만 고르면 idx_listings_delivery_filter 안에서 정렬과 (부피, 행정동) 조건이 끝난다.
        // 본문 컬럼까지 한 번에 읽으면 첫 페이지(ref 접근)에서 조건을 색인 안에서 거르지 못해 안 맞는 행까지 읽는다.
        return jdbcTemplate.query(
                SELECT_COLUMNS + " FROM listings JOIN (SELECT id FROM listings" + where + page + ") picked USING (id)"
                        + ORDER_BY_LATEST,
                ListingQueryRepository::mapRow,
                args.toArray()
        );
    }

    public Optional<MemberDong> findMemberDongByToken(String token) {
        return jdbcTemplate.query("SELECT dong_code FROM members WHERE token = ?",
                (resultSet, rowNum) -> new MemberDong(resultSet.getString("dong_code")), token)
                .stream().findFirst();
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
                resultSet.getBoolean("has_purchase_request"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getString("dong_code"),
                VolumeTier.valueOf(resultSet.getString("volume_tier"))
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
            boolean hasPurchaseRequest,
            Instant createdAt,
            String dongCode,
            VolumeTier volumeTier
    ) {
    }

    public record MemberDong(String dongCode) {
    }
}
