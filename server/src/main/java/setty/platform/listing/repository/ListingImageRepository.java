package setty.platform.listing.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import setty.platform.listing.domain.ListingImage;

public interface ListingImageRepository extends JpaRepository<ListingImage, Long> {

    List<ListingImage> findAllByListingIdOrderByDisplayOrderAsc(Long listingId);

    @Query("""
            select image from ListingImage image
            where image.listingId in :listingIds
              and image.displayOrder = (
                  select min(candidate.displayOrder) from ListingImage candidate
                  where candidate.listingId = image.listingId
              )
            """)
    List<ListingImage> findThumbnailsByListingIdIn(@Param("listingIds") Collection<Long> listingIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ListingImage image
            set image.displayOrder = image.displayOrder + :offset
            where image.listingId = :listingId
            """)
    void shiftDisplayOrders(@Param("listingId") Long listingId, @Param("offset") int offset);
}
