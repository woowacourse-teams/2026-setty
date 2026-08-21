package setty.prototype.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import setty.prototype.domain.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByPhoneNumber(String phoneNumber);
}
