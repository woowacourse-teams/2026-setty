package setty.platform.member.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import setty.platform.member.domain.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByLoginId(String loginId);

    Optional<Member> findByLoginId(String loginId);

    Optional<Member> findByToken(String token);

    // 회원 행을 잠그며 탈퇴 여부를 다시 읽는다. 요청 초반에 읽어 둔 회원 객체는 그 사이 끝난 탈퇴를 모르기 때문이다.
    // 잠금 읽기는 다른 트랜잭션이 커밋한 최신 행을 보므로, 탈퇴가 먼저 끝났으면 비어 있다.
    @Query(value = "select id from members where id = :id and withdrawn_at is null for update", nativeQuery = true)
    Optional<Long> lockActiveId(@Param("id") Long id);

    // 같은 회원의 주문끼리는 막지 않고 탈퇴와만 순서를 정한다.
    @Query(value = "select id from members where id = :id and withdrawn_at is null for share", nativeQuery = true)
    Optional<Long> lockActiveIdForShare(@Param("id") Long id);

    // 인스턴스가 둘이어도 같은 회원을 동시에 해석하지 않도록, 다른 쪽이 잠근 회원은 건너뛴다.
    @Query(value = """
            select * from members
            where id > :afterId and address_status is null
            order by id
            limit :size
            for update skip locked
            """, nativeQuery = true)
    List<Member> lockUnresolvedAfter(@Param("afterId") long afterId, @Param("size") int size);

    @Query(value = """
            select id from members
            where id > :afterId and address_status is null
            order by id
            limit :size
            """, nativeQuery = true)
    List<Long> findUnresolvedIdsAfter(@Param("afterId") long afterId, @Param("size") int size);

    @Query(value = "select count(*) from members where address_status is null", nativeQuery = true)
    long countUnresolved();

    @Query(value = "select * from members where id = :id and address_status is null for update skip locked",
            nativeQuery = true)
    Optional<Member> lockUnresolved(@Param("id") long id);
}
