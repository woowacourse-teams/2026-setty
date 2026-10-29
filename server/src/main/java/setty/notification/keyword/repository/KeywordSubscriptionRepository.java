package setty.notification.keyword.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import setty.notification.keyword.domain.KeywordSubscription;

public interface KeywordSubscriptionRepository extends JpaRepository<KeywordSubscription, Long> {

    List<KeywordSubscription> findAllByMemberIdOrderByCreatedAtAscIdAsc(Long memberId);

    long countByMemberId(Long memberId);

    boolean existsByMemberIdAndKeyword(Long memberId, String keyword);

    Optional<KeywordSubscription> findByIdAndMemberId(Long id, Long memberId);

    // 같은 회원의 구독 등록을 줄 세워 상한 검사와 저장 사이에 다른 요청이 끼어들지 못하게 한다.
    @Query(value = "SELECT id FROM members WHERE id = :memberId FOR UPDATE", nativeQuery = true)
    Optional<Long> lockMember(@Param("memberId") Long memberId);
}
