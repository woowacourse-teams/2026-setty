package setty.platform;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import setty.platform.listing.storage.ListingImageStorage;

/** 배송 조회에 제공하는 주문별 대표 사진. 매물 사진을 복사하지 않고 현재 첫 사진을 함께 읽는다. */
@Service
@RequiredArgsConstructor
public class DeliveryItemImageReader {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ListingImageStorage listingImageStorage;

    public Map<Long, String> findThumbnailUrls(final List<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        final Map<Long, String> thumbnails = new HashMap<>();
        jdbcTemplate.query("""
                SELECT o.id, image.object_key
                FROM orders o
                JOIN listing_images image ON image.listing_id = o.listing_id AND image.display_order = 1
                WHERE o.id IN (:orderIds)
                """, Map.of("orderIds", orderIds), resultSet -> {
            thumbnails.put(resultSet.getLong("id"),
                    listingImageStorage.publicUrl(resultSet.getString("object_key")));
        });
        return thumbnails;
    }
}
