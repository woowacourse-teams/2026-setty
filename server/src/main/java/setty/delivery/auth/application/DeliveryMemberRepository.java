package setty.delivery.auth.application;

import java.util.Optional;
import setty.delivery.auth.domain.DeliveryMember;

public interface DeliveryMemberRepository {

    boolean existsByLoginId(String loginId);

    Optional<DeliveryMember> findByLoginId(String loginId);

    Optional<DeliveryMember> findByToken(String token);

    DeliveryMember save(DeliveryMember member);
}
