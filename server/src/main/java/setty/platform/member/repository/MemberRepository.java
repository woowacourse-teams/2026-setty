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
