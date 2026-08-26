package setty.delivery.repository;

import java.util.List;
import java.util.Optional;
import setty.delivery.domain.DeliveryId;
import setty.delivery.domain.DriverId;
import setty.delivery.query.DeliveryRequest;
import setty.delivery.query.Shipment;

public interface DeliveryQueryRepository {

    List<DeliveryRequest.Summary> findAvailableRequests();

    Optional<DeliveryRequest.Detail> findAvailableRequestById(DeliveryId deliveryId);

    List<Shipment.Summary> findShipmentsByDriverId(DriverId driverId);

    Optional<Shipment.Detail> findShipmentByIdAndDriverId(DeliveryId deliveryId, DriverId driverId);
}
