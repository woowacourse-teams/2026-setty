package setty.delivery.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import setty.delivery.domain.DeliveryCancellation;
import setty.delivery.domain.OrderId;

public interface DeliveryCancellationRepository extends JpaRepository<DeliveryCancellation, Long> {

    boolean existsByOrderId(OrderId orderId);

    Optional<DeliveryCancellation> findByOrderId(OrderId orderId);
}
