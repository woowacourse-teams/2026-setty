package setty.notification.listing.application;

import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import setty.notification.listing.domain.ListingNotification;
import setty.notification.listing.repository.ListingNotificationRepository;

@Service
public class ListingNotificationService {

    private final ListingNotificationRepository listingNotificationRepository;
    private final JdbcTemplate jdbcTemplate;

    public ListingNotificationService(
            final ListingNotificationRepository listingNotificationRepository,
            final JdbcTemplate jdbcTemplate
    ) {
        this.listingNotificationRepository = listingNotificationRepository;
        this.jdbcTemplate = jdbcTemplate;
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

    @Transactional(readOnly = true)
    public List<ListingNotification> findMine(final Long memberId, final int page, final int size) {
        return listingNotificationRepository.findAllByMemberIdOrderByCreatedAtDescIdDesc(
                memberId, PageRequest.of(page, size));
    }
}
