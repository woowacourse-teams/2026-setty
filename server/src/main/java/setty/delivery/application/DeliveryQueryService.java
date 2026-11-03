package setty.delivery.application;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.delivery.application.readmodel.DeliveryRequest;
import setty.delivery.application.readmodel.Shipment;
import setty.delivery.domain.DeliveryId;
import setty.delivery.domain.DriverId;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.platform.DeliveryItemImageReader;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliveryQueryService {

    private final DeliveryQueryRepository deliveryQueryRepository;
    private final DeliveryItemImageReader deliveryItemImageReader;

    public Map<Long, String> findThumbnailUrls(final List<Long> orderIds) {
        return deliveryItemImageReader.findThumbnailUrls(orderIds);
    }

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
