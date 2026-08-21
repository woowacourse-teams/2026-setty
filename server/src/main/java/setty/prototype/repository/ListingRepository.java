package setty.prototype.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import setty.prototype.domain.Listing;

public interface ListingRepository extends JpaRepository<Listing, Long> {
    @EntityGraph(attributePaths = "images")
    List<Listing> findAllByOrderByCreatedAtDescIdDesc();

    @EntityGraph(attributePaths = "images")
    List<Listing> findAllBySellerIdOrderByCreatedAtDescIdDesc(Long sellerId);

    @EntityGraph(attributePaths = "images")
    Optional<Listing> findWithImagesById(Long id);
}
