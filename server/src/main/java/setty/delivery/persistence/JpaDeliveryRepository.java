package setty.delivery.persistence;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import setty.delivery.application.DeliveryRepository;
import setty.delivery.domain.Delivery;
import setty.delivery.domain.DeliveryId;
import setty.delivery.domain.OrderId;

@Repository
@RequiredArgsConstructor
public class JpaDeliveryRepository implements DeliveryRepository {

    private final SpringDataDeliveryRepository repository;

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
