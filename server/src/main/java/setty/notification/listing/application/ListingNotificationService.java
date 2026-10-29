package setty.notification.listing.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.notification.listing.domain.ListingNotification;
import setty.notification.listing.domain.NotificationStatus;
import setty.notification.listing.repository.ListingNotificationRepository;
import setty.notification.send.NotificationSender;
import setty.platform.MemberDeliveryArea;
import setty.platform.NotificationMemberReader;

@Service
public class ListingNotificationService {

    private static final int INSERT_BATCH_SIZE = 500;

    private static final String MATCHING_SUBSCRIPTIONS = """
            FROM keyword_subscriptions s
            WHERE ? LIKE CONCAT('%', s.keyword, '%')
              AND s.member_id <> ?
              AND (s.category IS NULL OR s.category = ?)
              AND (s.min_price IS NULL OR s.min_price <= ?)
              AND (s.max_price IS NULL OR s.max_price >= ?)
              AND NOT EXISTS (
                  SELECT 1 FROM keyword_exclusions e
                  WHERE e.subscription_id = s.id AND ? LIKE CONCAT('%', e.word, '%')
              )
            """;

    private final ListingNotificationRepository listingNotificationRepository;
    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final NotificationMemberReader notificationMemberReader;
    private final NotificationDeliveryFee notificationDeliveryFee;

    public ListingNotificationService(
            final ListingNotificationRepository listingNotificationRepository,
            final JdbcTemplate jdbcTemplate,
            final NotificationMemberReader notificationMemberReader,
            final NotificationDeliveryFee notificationDeliveryFee
    ) {
        this.listingNotificationRepository = listingNotificationRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.notificationMemberReader = notificationMemberReader;
        this.notificationDeliveryFee = notificationDeliveryFee;
    }

    // 배송비 조건이 없는 구독은 DB 안에서 한 번에 넣고, 배송비·총가격 조건이 있는 구독만 구독자마다 배송비를 계산해 넣는다.
    @Transactional
    public int createForListing(final ListingToNotify listing) {
        final int created = jdbcTemplate.update(
                """
                INSERT INTO listing_notifications (listing_id, member_id, status, attempts, created_at)
                SELECT DISTINCT ?, s.member_id, 'PENDING', 0, NOW(6)
                """ + MATCHING_SUBSCRIPTIONS + """
                  AND s.max_delivery_fee IS NULL AND s.max_total_price IS NULL
                ON DUPLICATE KEY UPDATE listing_id = listing_notifications.listing_id
                """,
                listing.listingId(), listing.title(), listing.sellerId(), listing.category(),
                listing.price(), listing.price(), listing.title()
        );
        return created + createForDeliveryFeeConditioned(listing);
    }

    private int createForDeliveryFeeConditioned(final ListingToNotify listing) {
        final List<DeliveryFeeCandidate> candidates = jdbcTemplate.query(
                "SELECT s.member_id, s.max_delivery_fee, s.max_total_price "
                        + MATCHING_SUBSCRIPTIONS
                        + "  AND (s.max_delivery_fee IS NOT NULL OR s.max_total_price IS NOT NULL)",
                (resultSet, rowNum) -> new DeliveryFeeCandidate(
                        resultSet.getLong("member_id"),
                        resultSet.getObject("max_delivery_fee", Integer.class),
                        resultSet.getObject("max_total_price", Integer.class)),
                listing.title(), listing.sellerId(), listing.category(), listing.price(), listing.price(), listing.title()
        );
        if (candidates.isEmpty()) {
            return 0;
        }
        final Map<Long, MemberDeliveryArea> areas = deliveryAreasOf(candidates);
        final Set<Long> accepted = new LinkedHashSet<>();
        for (final DeliveryFeeCandidate candidate : candidates) {
            final int deliveryFee = notificationDeliveryFee.of(listing, areas.get(candidate.memberId()));
            if (candidate.accepts(listing.price(), deliveryFee)) {
                accepted.add(candidate.memberId());
            }
        }
        return insertPending(listing.listingId(), new ArrayList<>(accepted));
    }

    private Map<Long, MemberDeliveryArea> deliveryAreasOf(final List<DeliveryFeeCandidate> candidates) {
        final List<Long> memberIds = candidates.stream().map(DeliveryFeeCandidate::memberId).distinct().toList();
        final Map<Long, MemberDeliveryArea> areas = new HashMap<>();
        for (int from = 0; from < memberIds.size(); from += INSERT_BATCH_SIZE) {
            final List<Long> chunk = memberIds.subList(from, Math.min(from + INSERT_BATCH_SIZE, memberIds.size()));
            for (final MemberDeliveryArea area : notificationMemberReader.findDeliveryAreas(chunk)) {
                areas.put(area.memberId(), area);
            }
        }
        return areas;
    }

    private int insertPending(final Long listingId, final List<Long> memberIds) {
        int created = 0;
        for (int from = 0; from < memberIds.size(); from += INSERT_BATCH_SIZE) {
            final List<Long> chunk = memberIds.subList(from, Math.min(from + INSERT_BATCH_SIZE, memberIds.size()));
            final int[] results = jdbcTemplate.batchUpdate(
                    """
                    INSERT INTO listing_notifications (listing_id, member_id, status, attempts, created_at)
                    VALUES (?, ?, 'PENDING', 0, NOW(6))
                    ON DUPLICATE KEY UPDATE listing_id = listing_notifications.listing_id
                    """,
                    chunk.stream().map(memberId -> new Object[]{listingId, memberId}).toList()
            );
            for (final int result : results) {
                created += result == 1 ? 1 : 0;
            }
        }
        return created;
    }

    public void markSent(final List<Long> notificationIds) {
        if (notificationIds.isEmpty()) {
            return;
        }
        namedJdbcTemplate.update(
                """
                UPDATE listing_notifications
                SET status = 'SENT', sent_at = NOW(6), attempts = attempts + 1
                WHERE id IN (:ids) AND status = 'PENDING'
                """,
                Map.of("ids", notificationIds)
        );
    }

    public long markFailedAttempt(final List<Long> notificationIds) {
        if (notificationIds.isEmpty()) {
            return 0;
        }
        namedJdbcTemplate.update(
                """
                UPDATE listing_notifications
                SET status = CASE WHEN attempts + 1 >= :max THEN 'FAILED' ELSE 'PENDING' END,
                    attempts = attempts + 1
                WHERE id IN (:ids) AND status = 'PENDING'
                """,
                Map.of("ids", notificationIds, "max", ListingNotification.MAXIMUM_ATTEMPTS)
        );
        return namedJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM listing_notifications WHERE id IN (:ids) AND status = 'PENDING'",
                Map.of("ids", notificationIds),
                Long.class
        );
    }

    public List<ListingNotification> findPendingBatch(final Long listingId, final Long afterId) {
        return listingNotificationRepository.findBatch(
                listingId, NotificationStatus.PENDING, afterId, PageRequest.of(0, NotificationSender.MAXIMUM_BATCH_SIZE));
    }

    public void ack(final Long memberId, final Long notificationId) {
        final int matched = jdbcTemplate.update(
                """
                UPDATE listing_notifications
                SET acked_at = COALESCE(acked_at, NOW(6))
                WHERE id = ? AND member_id = ?
                """,
                notificationId, memberId
        );
        if (matched == 0) {
            throw new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND);
        }
    }

    public long countUnread(final Long memberId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM listing_notifications
                WHERE member_id = ? AND status = 'SENT' AND read_at IS NULL
                """,
                Long.class,
                memberId
        );
    }

    public void read(final Long memberId, final Long notificationId) {
        final int matched = jdbcTemplate.update(
                """
                UPDATE listing_notifications
                SET read_at = COALESCE(read_at, NOW(6))
                WHERE id = ? AND member_id = ?
                """,
                notificationId, memberId
        );
        if (matched == 0) {
            throw new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND);
        }
    }

    public void readAll(final Long memberId) {
        jdbcTemplate.update(
                """
                UPDATE listing_notifications
                SET read_at = NOW(6)
                WHERE member_id = ? AND status = 'SENT' AND read_at IS NULL
                """,
                memberId
        );
    }

    @Transactional(readOnly = true)
    public List<ListingNotification> findMine(final Long memberId, final int page, final int size) {
        return listingNotificationRepository.findAllByMemberIdOrderByCreatedAtDescIdDesc(
                memberId, PageRequest.of(page, size));
    }

    private record DeliveryFeeCandidate(Long memberId, Integer maxDeliveryFee, Integer maxTotalPrice) {

        boolean accepts(final int price, final int deliveryFee) {
            return (maxDeliveryFee == null || deliveryFee <= maxDeliveryFee)
                    && (maxTotalPrice == null || price + deliveryFee <= maxTotalPrice);
        }
    }
}
