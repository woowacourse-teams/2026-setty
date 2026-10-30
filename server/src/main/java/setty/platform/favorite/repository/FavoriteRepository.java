package setty.platform.favorite.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import setty.platform.favorite.domain.Favorite;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    boolean existsByMemberIdAndListingId(Long memberId, Long listingId);

    void deleteByMemberIdAndListingId(Long memberId, Long listingId);

    @Query("select favorite.listingId from Favorite favorite where favorite.memberId = :memberId order by favorite.createdAt desc, favorite.id desc")
    List<Long> findListingIdsByMemberIdOrderByCreatedAtDesc(@Param("memberId") Long memberId);

    int countByListingId(Long listingId);

    @Modifying(flushAutomatically = true)
    @Query("delete from Favorite favorite where favorite.memberId = :memberId")
    void deleteAllByMemberId(@Param("memberId") Long memberId);

    @Query("""
            select new setty.platform.favorite.repository.FavoriteCount(favorite.listingId, count(favorite))
            from Favorite favorite
            where favorite.listingId in :listingIds
            group by favorite.listingId
            """)
    List<FavoriteCount> countByListingIdIn(@Param("listingIds") List<Long> listingIds);
}
