package setty.delivery.application;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import setty.delivery.application.readmodel.DeliveryRequest;
import setty.delivery.application.readmodel.DeliveryPage;
import setty.delivery.application.readmodel.Shipment;
import setty.delivery.domain.DeliveryId;
import setty.delivery.domain.DriverId;
import setty.deliveryfee.DeliveryFeeService;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.platform.DeliveryItemImageReader;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliveryQueryService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAXIMUM_PAGE_SIZE = 100;

    private final DeliveryQueryRepository deliveryQueryRepository;
    private final DeliveryItemImageReader deliveryItemImageReader;
    private final DeliveryFeeService deliveryFeeService;

    // 목록의 행마다 부르는 메모리 거리표 조회다. 행마다 읽기 트랜잭션(커넥션 획득·읽기 전용 설정·커밋)을 열지 않는다.
    @Transactional(propagation = Propagation.SUPPORTS)
    public Integer estimateDistanceMeters(final String pickupDongCode, final String deliveryDongCode) {
        return deliveryFeeService.distanceMeters(pickupDongCode, deliveryDongCode).orElse(null);
    }

    public Map<Long, String> findThumbnailUrls(final List<Long> orderIds) {
        return deliveryItemImageReader.findThumbnailUrls(orderIds);
    }

    public List<DeliveryRequest.Summary> findAvailableRequests() {
        return deliveryQueryRepository.findAvailableRequests();
    }

    public DeliveryPage<DeliveryRequest.Summary> findAvailableRequestPage(final DeliveryCursor cursor, final int size) {
        validateSize(size);
        final List<DeliveryRequest.Summary> rows = deliveryQueryRepository.findAvailableRequests(cursor, size + 1);
        final boolean hasNext = rows.size() > size;
        final List<DeliveryRequest.Summary> items = hasNext ? rows.subList(0, size) : rows;
        final DeliveryRequest.Summary last = hasNext ? items.getLast() : null;
        return new DeliveryPage<>(items,
                last == null ? null : new DeliveryCursor(last.requestedAt(), last.deliveryId()).encode());
    }

    public DeliveryRequest.Detail findAvailableRequest(final DeliveryId deliveryId) {
        return deliveryQueryRepository.findAvailableRequestById(deliveryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_NOT_FOUND));
    }

    public List<Shipment.Summary> findShipments(final DriverId driverId) {
        return deliveryQueryRepository.findShipmentsByDriverId(driverId);
    }

    public DeliveryPage<Shipment.Summary> findShipmentPage(final DriverId driverId, final ShipmentGroup group,
                                                         final DeliveryCursor cursor, final int size) {
        validateSize(size);
        final List<Shipment.Summary> rows = deliveryQueryRepository.findShipmentsByDriverId(driverId, group, cursor, size + 1);
        final boolean hasNext = rows.size() > size;
        final List<Shipment.Summary> items = hasNext ? rows.subList(0, size) : rows;
        final Shipment.Summary last = hasNext ? items.getLast() : null;
        return new DeliveryPage<>(items,
                last == null ? null : new DeliveryCursor(last.acceptedAt(), last.deliveryId()).encode());
    }

    private void validateSize(final int size) {
        if (size < 1 || size > MAXIMUM_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    public Shipment.Detail findShipment(final DeliveryId deliveryId, final DriverId driverId) {
        return deliveryQueryRepository.findShipmentByIdAndDriverId(deliveryId, driverId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_NOT_FOUND));
    }
}
