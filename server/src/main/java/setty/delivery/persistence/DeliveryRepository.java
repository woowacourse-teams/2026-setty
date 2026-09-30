package setty.delivery.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import setty.delivery.domain.OrderId;
import setty.delivery.domain.delivery.Delivery;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    boolean existsByOrderId(OrderId orderId);

    Optional<Delivery> findByOrderId(OrderId orderId);
}
