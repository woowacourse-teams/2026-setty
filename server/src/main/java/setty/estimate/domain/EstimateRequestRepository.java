package setty.estimate.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EstimateRequestRepository extends JpaRepository<EstimateRequest, Long> {
    List<EstimateRequest> findAllByOrderByCreatedAtDesc();
}
