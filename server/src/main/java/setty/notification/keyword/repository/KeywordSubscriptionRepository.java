package setty.notification.keyword.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import setty.notification.keyword.domain.KeywordSubscription;

public interface KeywordSubscriptionRepository extends JpaRepository<KeywordSubscription, Long> {

    List<KeywordSubscription> findAllByMemberIdOrderByCreatedAtAscIdAsc(Long memberId);

    long countByMemberId(Long memberId);

    Optional<KeywordSubscription> findByIdAndMemberId(Long id, Long memberId);
}
