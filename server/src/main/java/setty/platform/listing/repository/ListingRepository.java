package setty.platform.listing.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import setty.platform.listing.domain.Listing;
import setty.platform.listing.domain.SaleStatus;

public interface ListingRepository extends JpaRepository<Listing, Long> {

    List<Listing> findAllBySaleStatusAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(SaleStatus saleStatus);

    List<Listing> findAllBySellerIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(Long sellerId);

    Optional<Listing> findByIdAndDeletedAtIsNull(Long id);

    List<Listing> findAllByIdInAndDeletedAtIsNull(List<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select listing from Listing listing where listing.id = :id and listing.deletedAt is null")
    Optional<Listing> findActiveByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select listing from Listing listing where listing.id = :id")
    Optional<Listing> findByIdForUpdate(@Param("id") Long id);
}
