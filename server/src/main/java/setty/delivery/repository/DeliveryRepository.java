package setty.delivery.repository;

import java.util.Optional;
import setty.delivery.domain.Delivery;
import setty.delivery.domain.DeliveryId;
import setty.delivery.domain.OrderId;

public interface DeliveryRepository {

    boolean existsByOrderId(OrderId orderId);

    Optional<Delivery> findById(DeliveryId deliveryId);

    void save(Delivery delivery);
}
