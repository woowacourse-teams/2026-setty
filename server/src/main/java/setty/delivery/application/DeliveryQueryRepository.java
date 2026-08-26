package setty.delivery.application;

import java.util.List;
import java.util.Optional;
import setty.delivery.application.query.DeliveryRequest;
import setty.delivery.application.query.Shipment;
import setty.delivery.domain.DeliveryId;
import setty.delivery.domain.DriverId;

public interface DeliveryQueryRepository {

    List<DeliveryRequest.Summary> findAvailableRequests();

    Optional<DeliveryRequest.Detail> findAvailableRequestById(DeliveryId deliveryId);

    List<Shipment.Summary> findShipmentsByDriverId(DriverId driverId);

    Optional<Shipment.Detail> findShipmentByIdAndDriverId(DeliveryId deliveryId, DriverId driverId);
}
