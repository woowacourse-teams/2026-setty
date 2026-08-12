package setty.dispatch.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import setty.dispatch.domain.DispatchRequest;
import setty.dispatch.domain.DispatchStatus;

public interface DispatchRequestRepository extends JpaRepository<DispatchRequest, Long> {
    Optional<DispatchRequest> findByBuyerToken(String buyerToken);

    List<DispatchRequest> findAllByOrderByCreatedAtDescIdDesc();

    List<DispatchRequest> findAllByStatusOrderByCreatedAtDescIdDesc(DispatchStatus status);
}
