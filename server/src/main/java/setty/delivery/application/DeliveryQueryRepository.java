package setty.delivery.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    /** 칸이 있는 대기 요청 전부. 출발 칸, 도착 칸, 배송비 높은 순, 먼저 들어온 순으로 정렬한다. */
    List<DeliveryRequest.CellCall> findCellCalls();

    List<DeliveryRequest.Summary> findCellCalls(int pickupCell, int deliveryCell, int limit);

    /** 배송원마다 진행 중(수락·픽업) 배차의 도착 칸. 칸이 없는 배차는 뺀다. */
    Map<Long, Set<Integer>> findInProgressDeliveryCells(Collection<Long> driverIds);
}
