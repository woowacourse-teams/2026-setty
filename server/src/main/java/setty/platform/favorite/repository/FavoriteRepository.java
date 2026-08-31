package setty.platform.favorite.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import setty.platform.favorite.domain.Favorite;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    boolean existsByMemberIdAndListingId(Long memberId, Long listingId);

    void deleteByMemberIdAndListingId(Long memberId, Long listingId);

    @Query("select favorite.listingId from Favorite favorite where favorite.memberId = :memberId order by favorite.createdAt desc, favorite.id desc")
    List<Long> findListingIdsByMemberIdOrderByCreatedAtDesc(@Param("memberId") Long memberId);
}
