package setty.delivery.auth.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import setty.delivery.auth.domain.DeliveryMember;

interface SpringDataDeliveryMemberRepository extends JpaRepository<DeliveryMember, Long> {

    boolean existsByLoginId(String loginId);

    Optional<DeliveryMember> findByLoginId(String loginId);

    Optional<DeliveryMember> findByToken(String token);
}
