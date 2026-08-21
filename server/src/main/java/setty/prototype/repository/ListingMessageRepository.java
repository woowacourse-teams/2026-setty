package setty.prototype.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import setty.prototype.domain.ListingMessage;

public interface ListingMessageRepository extends JpaRepository<ListingMessage, Long> {
    List<ListingMessage> findAllByListingIdOrderByIdDesc(Long listingId);

    void deleteByListingId(Long listingId);

    @Query("""
            select m.listing.id as listingId,
                   count(m) as messageCount,
                   max(m.createdAt) as latestMessageAt
            from ListingMessage m
            where m.listing.id in :listingIds
            group by m.listing.id
            """)
    List<ListingMessageStat> findStatsByListingIds(@Param("listingIds") List<Long> listingIds);
}
