package setty.delivery.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import setty.delivery.domain.Delivery;
import setty.delivery.domain.OrderId;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    boolean existsByOrderId(OrderId orderId);

    Optional<Delivery> findByOrderId(OrderId orderId);
}
