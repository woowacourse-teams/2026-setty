package setty.delivery.application;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.delivery.domain.DeliveryId;
import setty.delivery.domain.DriverId;
import setty.delivery.query.DeliveryRequest;
import setty.delivery.query.Shipment;
import setty.delivery.repository.DeliveryQueryRepository;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliveryQueryService {

    private final DeliveryQueryRepository deliveryQueryRepository;

    public List<DeliveryRequest.Summary> findAvailableRequests() {
        return deliveryQueryRepository.findAvailableRequests();
    }

    public DeliveryRequest.Detail findAvailableRequest(final DeliveryId deliveryId) {
        return deliveryQueryRepository.findAvailableRequestById(deliveryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_NOT_FOUND));
    }

    public List<Shipment.Summary> findShipments(final DriverId driverId) {
        return deliveryQueryRepository.findShipmentsByDriverId(driverId);
    }

    public Shipment.Detail findShipment(final DeliveryId deliveryId, final DriverId driverId) {
        return deliveryQueryRepository.findShipmentByIdAndDriverId(deliveryId, driverId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_NOT_FOUND));
    }
}
