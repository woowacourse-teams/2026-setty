package setty.estimate.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManualNotificationRepository extends JpaRepository<ManualNotification, Long> {
    Optional<ManualNotification> findByEstimateRequestId(Long estimateRequestId);
}
