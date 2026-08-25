package setty.platform.member.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import setty.platform.member.domain.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByLoginId(String loginId);
}
