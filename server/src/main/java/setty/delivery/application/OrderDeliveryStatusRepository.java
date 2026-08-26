package setty.delivery.application;

import java.util.Optional;
import setty.delivery.domain.OrderDeliveryState;
import setty.delivery.domain.OrderId;

public interface OrderDeliveryStatusRepository {

    Optional<OrderDeliveryState> findById(OrderId orderId);

    void save(OrderDeliveryState orderDeliveryState);
}
