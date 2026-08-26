package setty.delivery.auth.persistence;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import setty.delivery.auth.application.DeliveryMemberRepository;
import setty.delivery.auth.domain.DeliveryMember;

@Repository
@RequiredArgsConstructor
public class JpaDeliveryMemberRepository implements DeliveryMemberRepository {

    private final SpringDataDeliveryMemberRepository repository;

    @Override
    public boolean existsByLoginId(final String loginId) {
        return repository.existsByLoginId(loginId);
    }

    @Override
    public Optional<DeliveryMember> findByLoginId(final String loginId) {
        return repository.findByLoginId(loginId);
    }

    @Override
    public Optional<DeliveryMember> findByToken(final String token) {
        return repository.findByToken(token);
    }

    @Override
    public DeliveryMember save(final DeliveryMember member) {
        return repository.saveAndFlush(member);
    }
}
