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
import setty.deliveryfee.ZoneRange;
import setty.platform.listing.domain.ConditionGrade;
import setty.platform.listing.domain.ListingCategory;
import setty.platform.listing.domain.ListingSearchTerms;

// 목록은 JPA 엔티티 대신 JDBC로 읽는다. OSIV 아래에서 JPA로 조회하면 응답이 끝날 때까지 커넥션을 붙잡지만,
// 트랜잭션 밖의 JdbcTemplate은 쿼리가 끝나면 바로 반납한다.
@Repository
public class ListingQueryRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, title, price, delivery_fee, category, condition_grade,
                   width_cm, depth_cm, height_cm, has_purchase_request, created_at, dong_code, zip_zone, volume_tier""";
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
            Instant cursorCreatedAt, Long cursorId, Integer limit,
            Map<VolumeTier, List<String>> sellerDongCodes,
            Map<VolumeTier, List<ZoneRange>> sellerZipZones,
            ListingSearchTerms search, ListingCategory category, List<ConditionGrade> conditionGrades,
            Integer minPrice, Integer maxPrice
    ) {
        StringBuilder where = new StringBuilder(" WHERE deleted_at IS NULL AND sale_status = 'AVAILABLE'");
        List<Object> args = new ArrayList<>();
        if (search != null) {
            where.append(" AND (").append(searchCondition(search, args)).append(")");
        }
        if (category != null) {
            where.append(" AND category = ?");
            args.add(category.name());
        }
        if (conditionGrades != null) {
            where.append(" AND condition_grade IN (")
                    .append(String.join(",", Collections.nCopies(conditionGrades.size(), "?"))).append(")");
            conditionGrades.forEach(grade -> args.add(grade.name()));
        }
        if (minPrice != null) {
            where.append(" AND price >= ?");
            args.add(minPrice);
        }
        if (maxPrice != null) {
            where.append(" AND price <= ?");
            args.add(maxPrice);
        }
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
        if (sellerZipZones != null) {
            List<String> conditions = new ArrayList<>();
            sellerZipZones.forEach((volumeTier, ranges) -> {
                conditions.add("(volume_tier = ? AND (" + zipZoneCondition(ranges, args, volumeTier) + "))");
            });
            where.append(" AND (").append(String.join(" OR ", conditions)).append(")");
        }
        String page = ORDER_BY_LATEST;
        if (limit != null) {
            page += " LIMIT ?";
            args.add(limit);
        }
        if (sellerDongCodes == null && sellerZipZones == null) {
            return jdbcTemplate.query(SELECT_COLUMNS + " FROM listings" + where + page,
                    ListingQueryRepository::mapRow, args.toArray());
        }
        // 배송비 조건으로 먼저 id를 고른다. 검색어가 있으면 제목·설명 확인을 위한 본문 접근도 필요하다.
        // 본문 컬럼까지 한 번에 읽으면 첫 페이지(ref 접근)에서 조건을 색인 안에서 거르지 못해 안 맞는 행까지 읽는다.
        return jdbcTemplate.query(
                SELECT_COLUMNS + " FROM listings JOIN (SELECT id FROM listings" + where + page + ") picked USING (id)"
                        + ORDER_BY_LATEST,
                ListingQueryRepository::mapRow,
                args.toArray()
        );
    }

    // 검색 단어마다 대소문자를 무시한 제목·설명 부분 일치를 건다. %, _도 와일드카드가 아닌 문자다.
    // 동의어 묶음에 속한 검색어면 묶음 카테고리의 매물도 함께 찾는다.
    private static String searchCondition(ListingSearchTerms search, List<Object> args) {
        List<String> parts = new ArrayList<>();
        for (String word : search.words()) {
            for (String column : List.of("title", "description")) {
                parts.add("LOCATE(CAST(LOWER(?) AS BINARY), CAST(LOWER(" + column + ") AS BINARY)) > 0");
                args.add(word);
            }
        }
        if (search.category() != null) {
            parts.add("category = ?");
            args.add(search.category().name());
        }
        return String.join(" OR ", parts);
    }

    // 이어진 번호가 셋 이상이면 BETWEEN 하나로, 짧은 조각은 IN 하나로 모은다. 요청당 조건 수가 줄어 DB가 덜 일한다.
    private static String zipZoneCondition(List<ZoneRange> ranges, List<Object> args, VolumeTier volumeTier) {
        args.add(volumeTier.name());
        List<String> parts = new ArrayList<>();
        List<Integer> singles = new ArrayList<>();
        for (ZoneRange range : ranges) {
            if (range.to() - range.from() >= 2) {
                parts.add("zip_zone BETWEEN ? AND ?");
                args.add(range.from());
                args.add(range.to());
            } else {
                for (int zone = range.from(); zone <= range.to(); zone++) {
                    singles.add(zone);
                }
            }
        }
        if (!singles.isEmpty()) {
            parts.add("zip_zone IN (" + String.join(",", Collections.nCopies(singles.size(), "?")) + ")");
            args.addAll(singles);
        }
        return String.join(" OR ", parts);
    }

    public Optional<MemberArea> findMemberAreaByToken(String token) {
        return jdbcTemplate.query("SELECT id, dong_code, zip_code FROM members WHERE token = ?",
                (resultSet, rowNum) -> new MemberArea(resultSet.getLong("id"), resultSet.getString("dong_code"),
                        resultSet.getString("zip_code")),
                token)
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

    public Map<Long, Integer> findFavoriteCounts(List<Long> listingIds) {
        String placeholders = String.join(",", Collections.nCopies(listingIds.size(), "?"));
        Map<Long, Integer> counts = new HashMap<>();
        jdbcTemplate.query(
                "SELECT listing_id, COUNT(*) AS favorite_count FROM favorites WHERE listing_id IN (" + placeholders
                        + ") GROUP BY listing_id",
                (ResultSet resultSet) -> {
                    counts.put(resultSet.getLong("listing_id"), resultSet.getInt("favorite_count"));
                },
                listingIds.toArray()
        );
        return counts;
    }

    // 매물마다 걸린 열린 주문(결제 대기·결제 완료·취소 대기)의 상태. 목록 카드의 "결제 중"과 "거래중"을 가른다.
    public Map<Long, List<String>> findOpenOrderStatuses(List<Long> listingIds) {
        String placeholders = String.join(",", Collections.nCopies(listingIds.size(), "?"));
        Map<Long, List<String>> statuses = new HashMap<>();
        jdbcTemplate.query(
                "SELECT listing_id, order_status FROM orders WHERE order_status IN ('PENDING', 'CONFIRMED', 'CANCEL_PENDING')"
                        + " AND listing_id IN (" + placeholders + ")",
                (ResultSet resultSet) -> {
                    statuses.computeIfAbsent(resultSet.getLong("listing_id"), id -> new ArrayList<>())
                            .add(resultSet.getString("order_status"));
                },
                listingIds.toArray()
        );
        return statuses;
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
                resultSet.getObject("zip_zone", Integer.class),
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
            Integer zipZone,
            VolumeTier volumeTier
    ) {
    }

    public record MemberArea(Long id, String dongCode, String zipCode) {
    }
}
