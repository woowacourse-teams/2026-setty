package setty.delivery.application;

import java.util.List;
import java.util.Optional;
import setty.delivery.application.readmodel.DeliveryRequest;
import setty.delivery.application.readmodel.Shipment;
import setty.delivery.domain.DeliveryId;
import setty.delivery.domain.DriverId;

public interface DeliveryQueryRepository {

    List<DeliveryRequest.Summary> findAvailableRequests();

    List<DeliveryRequest.Summary> findAvailableRequests(DeliveryCursor cursor, Integer limit);

    Optional<DeliveryRequest.Detail> findAvailableRequestById(DeliveryId deliveryId);

    List<Shipment.Summary> findShipmentsByDriverId(DriverId driverId);

    List<Shipment.Summary> findShipmentsByDriverId(DriverId driverId, ShipmentGroup group,
                                                 DeliveryCursor cursor, Integer limit);

    Optional<Shipment.Detail> findShipmentByIdAndDriverId(DeliveryId deliveryId, DriverId driverId);
}
