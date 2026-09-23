package setty.delivery.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import setty.delivery.domain.Delivery;
import setty.delivery.domain.OrderId;

interface SpringDataDeliveryRepository extends JpaRepository<Delivery, Long> {

    boolean existsByOrderId(OrderId orderId);
}
