package setty.delivery.repository;

import java.util.Optional;
import org.springframework.stereotype.Repository;
import setty.delivery.domain.Delivery;
import setty.delivery.domain.DeliveryId;
import setty.delivery.domain.OrderId;

@Repository
public class JpaDeliveryRepository implements DeliveryRepository {

    private final SpringDataDeliveryRepository repository;

    public JpaDeliveryRepository(final SpringDataDeliveryRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existsByOrderId(final OrderId orderId) {
        return repository.existsByOrderId(orderId);
    }

    @Override
    public Optional<Delivery> findById(final DeliveryId deliveryId) {
        return repository.findById(deliveryId.value());
    }

    @Override
    public void save(final Delivery delivery) {
        repository.save(delivery);
    }
}
