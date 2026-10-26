package setty.platform.listing.application;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import setty.deliveryfee.DeliveryFeeService;

// 구역 도입 전 매물에 판매자의 지금 우편번호로 구역을 채운다. 회원 주소 채우기(AddressBackfillJob)가 끝난 뒤 켠다.
// 등록 뒤 이사한 판매자는 등록 때 주소를 알 수 없어 지금 주소로 채운다.
@Component
@ConditionalOnProperty(name = "setty.listing.zip-zone-backfill.enabled", havingValue = "true")
public class ListingZipZoneBackfillJob {

    private static final Logger log = LoggerFactory.getLogger(ListingZipZoneBackfillJob.class);

    private final JdbcTemplate jdbcTemplate;
    private final DeliveryFeeService deliveryFeeService;
    private final int batchSize;
    private final Duration pause;

    public ListingZipZoneBackfillJob(
            final JdbcTemplate jdbcTemplate,
            final DeliveryFeeService deliveryFeeService,
            @Value("${setty.listing.zip-zone-backfill.batch-size}") final int batchSize,
            @Value("${setty.listing.zip-zone-backfill.pause}") final Duration pause
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.deliveryFeeService = deliveryFeeService;
        this.batchSize = batchSize;
        this.pause = pause;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        Thread.ofPlatform().name("listing-zip-zone-backfill").daemon().start(this::run);
    }

    public int run() {
        final long startedAt = System.nanoTime();
        long afterId = 0;
        int filled = 0;
        int skipped = 0;
        while (true) {
            final List<SellerZip> rows = jdbcTemplate.query("""
                            SELECT l.id, m.zip_code FROM listings l JOIN members m ON m.id = l.seller_id
                            WHERE l.id > ? AND l.zip_zone IS NULL AND l.deleted_at IS NULL AND m.zip_code IS NOT NULL
                            ORDER BY l.id LIMIT ?""",
                    (resultSet, rowNum) -> new SellerZip(resultSet.getLong("id"), resultSet.getString("zip_code")),
                    afterId, batchSize);
            if (rows.isEmpty()) {
                break;
            }
            afterId = rows.getLast().listingId();
            final List<Object[]> updates = new ArrayList<>();
            for (final SellerZip row : rows) {
                deliveryFeeService.zipZoneOf(row.zipCode())
                        .ifPresent(zone -> updates.add(new Object[]{zone, row.listingId()}));
            }
            jdbcTemplate.batchUpdate("UPDATE listings SET zip_zone = ? WHERE id = ? AND zip_zone IS NULL", updates);
            filled += updates.size();
            skipped += rows.size() - updates.size();
            if (!sleep()) {
                break;
            }
        }
        log.info("매물 우편번호 구역 채우기 완료. filled={}, skippedOutsideZones={}, elapsedMillis={}, batchSize={}, pause={}",
                filled, skipped, Duration.ofNanos(System.nanoTime() - startedAt).toMillis(), batchSize, pause);
        return filled;
    }

    private boolean sleep() {
        if (pause.isZero()) {
            return true;
        }
        try {
            Thread.sleep(pause);
            return true;
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private record SellerZip(long listingId, String zipCode) {
    }
}
