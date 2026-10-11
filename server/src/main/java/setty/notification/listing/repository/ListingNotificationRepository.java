package setty.notification.listing.repository;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import setty.notification.listing.domain.ListingNotification;
import setty.notification.listing.domain.NotificationStatus;

public interface ListingNotificationRepository extends JpaRepository<ListingNotification, Long> {

    @Query("""
            select n from ListingNotification n
            where n.listingId = :listingId and n.status = :status and n.id > :afterId
            order by n.id asc
            """)
    List<ListingNotification> findBatch(
            @Param("listingId") Long listingId,
            @Param("status") NotificationStatus status,
            @Param("afterId") Long afterId,
            Pageable pageable
    );

    List<ListingNotification> findAllByMemberIdOrderByCreatedAtDescIdDesc(Long memberId, Pageable pageable);

    long countByListingIdAndStatus(Long listingId, NotificationStatus status);
}
