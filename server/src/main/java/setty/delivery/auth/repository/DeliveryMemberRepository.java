package setty.delivery.auth.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import setty.delivery.auth.domain.DeliveryMember;

public interface DeliveryMemberRepository extends JpaRepository<DeliveryMember, Long> {

    boolean existsByLoginId(String loginId);

    Optional<DeliveryMember> findByLoginId(String loginId);

    Optional<DeliveryMember> findByToken(String token);
}
