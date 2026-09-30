package setty.delivery.persistence;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import setty.delivery.application.DeliveryRepository;
import setty.delivery.domain.Delivery;
import setty.delivery.domain.DeliveryCancellation;
import setty.delivery.domain.DeliveryId;
import setty.delivery.domain.OrderId;

@Repository
@RequiredArgsConstructor
public class JpaDeliveryRepository implements DeliveryRepository {

    private final SpringDataDeliveryRepository repository;
    private final SpringDataDeliveryCancellationRepository cancellationRepository;

    @Override
    public boolean existsByOrderId(final OrderId orderId) {
        return repository.existsByOrderId(orderId);
    }

    @Override
    public Optional<Delivery> findById(final DeliveryId deliveryId) {
        return repository.findById(deliveryId.value());
    }

    @Override
    public Optional<Delivery> findByOrderId(final OrderId orderId) {
        return repository.findByOrderId(orderId);
    }

    @Override
    public void save(final Delivery delivery) {
        repository.save(delivery);
    }

    @Override
    public boolean existsCancellationByOrderId(final OrderId orderId) {
        return cancellationRepository.existsByOrderId(orderId);
    }

    @Override
    public Optional<DeliveryCancellation> findCancellationByOrderId(final OrderId orderId) {
        return cancellationRepository.findByOrderId(orderId);
    }

    @Override
    public void saveCancellation(final DeliveryCancellation cancellation) {
        cancellationRepository.save(cancellation);
    }
}
