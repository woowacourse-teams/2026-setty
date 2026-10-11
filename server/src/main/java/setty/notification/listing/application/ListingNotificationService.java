package setty.notification.listing.application;

import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import setty.notification.listing.domain.ListingNotification;
import setty.notification.listing.domain.NotificationStatus;
import setty.notification.listing.repository.ListingNotificationRepository;
import setty.notification.send.NotificationSender;

@Service
public class ListingNotificationService {

    private final ListingNotificationRepository listingNotificationRepository;
    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public ListingNotificationService(
            final ListingNotificationRepository listingNotificationRepository,
            final JdbcTemplate jdbcTemplate
    ) {
        this.listingNotificationRepository = listingNotificationRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int createForListing(final Long listingId, final Long sellerId, final String title) {
        return jdbcTemplate.update(
                """
                INSERT INTO listing_notifications (listing_id, member_id, status, attempts, created_at)
                SELECT DISTINCT ?, s.member_id, 'PENDING', 0, NOW(6)
                FROM keyword_subscriptions s
                WHERE ? LIKE CONCAT('%', s.keyword, '%')
                  AND s.member_id <> ?
                """,
                listingId, title, sellerId
        );
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

    public void markFailedAttempt(final List<Long> notificationIds) {
        if (notificationIds.isEmpty()) {
            return;
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
    }

    public long countPending(final Long listingId) {
        return listingNotificationRepository.countByListingIdAndStatus(listingId, NotificationStatus.PENDING);
    }

    public List<ListingNotification> findPendingBatch(final Long listingId, final Long afterId) {
        return listingNotificationRepository.findBatch(
                listingId, NotificationStatus.PENDING, afterId, PageRequest.of(0, NotificationSender.MAXIMUM_BATCH_SIZE));
    }

    @Transactional(readOnly = true)
    public List<ListingNotification> findMine(final Long memberId, final int page, final int size) {
        return listingNotificationRepository.findAllByMemberIdOrderByCreatedAtDescIdDesc(
                memberId, PageRequest.of(page, size));
    }
}
