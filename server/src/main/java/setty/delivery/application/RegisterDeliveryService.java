package setty.delivery.application;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.common.OrderRequested;
import setty.delivery.domain.Address;
import setty.delivery.domain.Delivery;
import setty.delivery.domain.DeliveryRoute;
import setty.delivery.domain.EstimatedDeliveryFee;
import setty.delivery.domain.FurnitureInfo;
import setty.delivery.domain.OrderId;
import setty.delivery.domain.PhoneNumber;
import setty.delivery.repository.DeliveryRepository;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

@Service
public class RegisterDeliveryService {

    private final DeliveryRepository deliveryRepository;

    public RegisterDeliveryService(final DeliveryRepository deliveryRepository) {
        this.deliveryRepository = deliveryRepository;
    }

    @Transactional
    public void register(final OrderRequested event, final Instant requestedAt) {
        if (event == null || requestedAt == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        final OrderId orderId = new OrderId(event.orderId());
        if (deliveryRepository.existsByOrderId(orderId)) {
            return;
        }

        final Delivery delivery = Delivery.request(
                orderId,
                new FurnitureInfo(event.itemName(), event.category()),
                new DeliveryRoute(
                        new Address(event.pickupAddress()),
                        new Address(event.deliveryAddress()),
                        new PhoneNumber(event.pickupPhoneNumber()),
                        new PhoneNumber(event.deliveryPhoneNumber())
                ),
                new EstimatedDeliveryFee(event.deliveryFee()),
                requestedAt
        );
        deliveryRepository.save(delivery);
    }
}
