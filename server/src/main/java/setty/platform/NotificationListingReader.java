package setty.platform;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import setty.deliveryfee.VolumeTier;
import setty.platform.listing.domain.SaleStatus;

@Service
public class NotificationListingReader {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public NotificationListingReader(final NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // 지난 알림도 보여 줘야 해서 삭제된 매물을 거르지 않는다.
    public Map<Long, NotifiedListing> findAll(final List<Long> listingIds) {
        if (listingIds.isEmpty()) {
            return Map.of();
        }
        final Map<Long, NotifiedListing> listings = new HashMap<>();
        jdbcTemplate.query("""
                SELECT id, title, price, dong_code, zip_zone, volume_tier, delivery_fee,
                       deleted_at, sale_status, has_purchase_request
                FROM listings WHERE id IN (:ids)
                """, Map.of("ids", listingIds), resultSet -> {
                    final NotifiedListing listing = new NotifiedListing(
                            resultSet.getLong("id"),
                            resultSet.getString("title"),
                            resultSet.getInt("price"),
                            resultSet.getString("dong_code"),
                            resultSet.getObject("zip_zone", Integer.class),
                            VolumeTier.valueOf(resultSet.getString("volume_tier")),
                            resultSet.getInt("delivery_fee"),
                            tradeStatusOf(resultSet)
                    );
                    listings.put(listing.id(), listing);
                });
        return listings;
    }

    private static String tradeStatusOf(final ResultSet resultSet) throws SQLException {
        if (resultSet.getTimestamp("deleted_at") != null) {
            return "DELETED";
        }
        final SaleStatus saleStatus = SaleStatus.valueOf(resultSet.getString("sale_status"));
        if (saleStatus == SaleStatus.SOLD) {
            return "SOLD";
        }
        if (saleStatus == SaleStatus.RESERVED || resultSet.getBoolean("has_purchase_request")) {
            return "TRADING";
        }
        return "AVAILABLE";
    }
}
