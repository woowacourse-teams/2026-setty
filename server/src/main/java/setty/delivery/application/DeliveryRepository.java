package setty.delivery.application;

import java.util.Optional;
import setty.delivery.domain.Delivery;
import setty.delivery.domain.DeliveryCancellation;
import setty.delivery.domain.DeliveryId;
import setty.delivery.domain.OrderId;

public interface DeliveryRepository {

    boolean existsByOrderId(OrderId orderId);

    Optional<Delivery> findById(DeliveryId deliveryId);

    Optional<Delivery> findByOrderId(OrderId orderId);

    void save(Delivery delivery);

    boolean existsCancellationByOrderId(OrderId orderId);

    Optional<DeliveryCancellation> findCancellationByOrderId(OrderId orderId);

    void saveCancellation(DeliveryCancellation cancellation);
}
