package setty.delivery.application;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.delivery.domain.OrderId;
import setty.delivery.persistence.DeliveryRepository;
import setty.platform.DeliverySupportReader;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliverySupportQuery implements DeliverySupportReader {
    private final DeliveryRepository repository;

    @Override
    public Optional<State> findState(final Long orderId) {
        return repository.findByOrderId(new OrderId(orderId))
                .map(delivery -> State.valueOf(delivery.getStatus().name()));
    }
}
