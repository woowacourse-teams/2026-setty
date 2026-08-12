package setty.dispatch.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import setty.dispatch.domain.SellerInputSession;

public interface SellerInputSessionRepository extends JpaRepository<SellerInputSession, Long> {
    Optional<SellerInputSession> findByToken(String token);

    Optional<SellerInputSession> findByDispatchRequestId(Long dispatchRequestId);
}
