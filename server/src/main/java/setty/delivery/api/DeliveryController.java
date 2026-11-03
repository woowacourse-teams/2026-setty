package setty.delivery.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import setty.delivery.api.dto.DeliveryRequestDetailResponse;
import setty.delivery.api.dto.DeliveryPageResponse;
import setty.delivery.api.dto.DeliveryRequestSummaryResponse;
import setty.delivery.api.dto.ShipmentDetailResponse;
import setty.delivery.api.dto.ShipmentSummaryResponse;
import setty.delivery.application.DeliveryLifecycleService;
import setty.delivery.application.DeliveryCursor;
import setty.delivery.application.DeliveryQueryService;
import setty.delivery.application.ShipmentGroup;
import setty.delivery.application.readmodel.DeliveryPage;
import setty.delivery.application.readmodel.DeliveryRequest;
import setty.delivery.application.readmodel.Shipment;
import setty.delivery.auth.domain.DeliveryMember;
import setty.delivery.domain.DeliveryId;
import setty.delivery.domain.DriverId;
import setty.global.auth.LoginDeliveryMember;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/delivery")
public class DeliveryController {

    private final DeliveryQueryService deliveryQueryService;
    private final DeliveryLifecycleService deliveryLifecycleService;
    private final DeliveryRequestEventStream deliveryRequestEventStream;

    @GetMapping("/requests")
    public ResponseEntity<?> findRequests(
            @LoginDeliveryMember final DeliveryMember member,
            @RequestParam(required = false) final String cursor,
            @RequestParam(required = false) final Integer size
    ) {
        authenticatedDriverId(member);
        // 웹은 size를 지정해 페이지로 읽는다. 내 정보의 전체 집계 등 기존 배열 조회는 유지한다.
        if (cursor == null && size == null) {
            return ResponseEntity.ok(requestResponses(deliveryQueryService.findAvailableRequests()));
        }
        final DeliveryPage<DeliveryRequest.Summary> page = deliveryQueryService.findAvailableRequestPage(
                DeliveryCursor.parse(cursor), size == null ? DeliveryQueryService.DEFAULT_PAGE_SIZE : size);
        return ResponseEntity.ok(new DeliveryPageResponse<>(requestResponses(page.items()), page.nextCursor()));
    }

    private List<DeliveryRequestSummaryResponse> requestResponses(final List<DeliveryRequest.Summary> requests) {
        final Map<Long, String> thumbnails = deliveryQueryService.findThumbnailUrls(
                requests.stream().map(DeliveryRequest.Summary::orderId).toList());
        return requests.stream()
                .map(request -> DeliveryRequestSummaryResponse.from(request, thumbnails.get(request.orderId()),
                        deliveryQueryService.estimateDistanceMeters(request.pickupDongCode(), request.deliveryDongCode())))
                .toList();
    }

    @GetMapping(value = "/requests/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> subscribeRequestEvents(
            @LoginDeliveryMember final DeliveryMember member
    ) {
        final DriverId driverId = authenticatedDriverId(member);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .header("X-Accel-Buffering", "no")
                .body(deliveryRequestEventStream.subscribe(driverId));
    }

    @GetMapping("/requests/{deliveryId}")
    public ResponseEntity<DeliveryRequestDetailResponse> findRequest(
            @LoginDeliveryMember final DeliveryMember member,
            @PathVariable final long deliveryId
    ) {
        authenticatedDriverId(member);
        final DeliveryRequest.Detail request = deliveryQueryService.findAvailableRequest(new DeliveryId(deliveryId));
        final String thumbnail = deliveryQueryService.findThumbnailUrls(List.of(request.orderId())).get(request.orderId());
        return ResponseEntity.ok(DeliveryRequestDetailResponse.from(request, thumbnail,
                deliveryQueryService.estimateDistanceMeters(request.pickupDongCode(), request.deliveryDongCode())));
    }

    @PostMapping("/requests/{deliveryId}")
    public ResponseEntity<Void> accept(
            @LoginDeliveryMember final DeliveryMember member,
            @PathVariable final long deliveryId
    ) {
        deliveryLifecycleService.accept(
                new DeliveryId(deliveryId),
                authenticatedDriverId(member),
                Instant.now()
        );
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/shipments")
    public ResponseEntity<?> findShipments(
            @LoginDeliveryMember final DeliveryMember member,
            @RequestParam(required = false) final String cursor,
            @RequestParam(required = false) final Integer size,
            @RequestParam(required = false) final ShipmentGroup group
    ) {
        final DriverId driverId = authenticatedDriverId(member);
        if (cursor == null && size == null && group == null) {
            // 내 정보의 전체 합산에는 사진과 거리가 필요하지 않아 조회를 생략한다.
            return ResponseEntity.ok(deliveryQueryService.findShipments(driverId).stream()
                    .map(shipment -> ShipmentSummaryResponse.from(shipment, null, null))
                    .toList());
        }
        final DeliveryPage<Shipment.Summary> page = deliveryQueryService.findShipmentPage(driverId, group,
                DeliveryCursor.parse(cursor), size == null ? DeliveryQueryService.DEFAULT_PAGE_SIZE : size);
        return ResponseEntity.ok(new DeliveryPageResponse<>(shipmentResponses(page.items()), page.nextCursor()));
    }

    private List<ShipmentSummaryResponse> shipmentResponses(final List<Shipment.Summary> shipments) {
        final Map<Long, String> thumbnails = deliveryQueryService.findThumbnailUrls(
                shipments.stream().map(Shipment.Summary::orderId).toList());
        return shipments.stream()
                .map(shipment -> ShipmentSummaryResponse.from(shipment, thumbnails.get(shipment.orderId()),
                        deliveryQueryService.estimateDistanceMeters(shipment.pickupDongCode(), shipment.deliveryDongCode())))
                .toList();
    }

    @GetMapping("/shipments/{deliveryId}")
    public ResponseEntity<ShipmentDetailResponse> findShipment(
            @LoginDeliveryMember final DeliveryMember member,
            @PathVariable final long deliveryId
    ) {
        final Shipment.Detail shipment = deliveryQueryService.findShipment(
                new DeliveryId(deliveryId), authenticatedDriverId(member));
        final String thumbnail = deliveryQueryService.findThumbnailUrls(List.of(shipment.orderId())).get(shipment.orderId());
        return ResponseEntity.ok(ShipmentDetailResponse.from(shipment, thumbnail,
                deliveryQueryService.estimateDistanceMeters(shipment.pickupDongCode(), shipment.deliveryDongCode())));
    }

    @PostMapping("/shipments/{deliveryId}/pickup")
    public ResponseEntity<Void> pickUp(
            @LoginDeliveryMember final DeliveryMember member,
            @PathVariable final long deliveryId
    ) {
        deliveryLifecycleService.pickUp(
                new DeliveryId(deliveryId),
                authenticatedDriverId(member),
                Instant.now()
        );
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/shipments/{deliveryId}/cancellation")
    public ResponseEntity<Void> cancelAssignment(
            @LoginDeliveryMember final DeliveryMember member,
            @PathVariable final long deliveryId
    ) {
        deliveryLifecycleService.cancelAssignment(
                new DeliveryId(deliveryId),
                authenticatedDriverId(member),
                Instant.now()
        );
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/shipments/{deliveryId}/completion")
    public ResponseEntity<Void> complete(
            @LoginDeliveryMember final DeliveryMember member,
            @PathVariable final long deliveryId
    ) {
        deliveryLifecycleService.complete(
                new DeliveryId(deliveryId),
                authenticatedDriverId(member),
                Instant.now()
        );
        return ResponseEntity.noContent().build();
    }

    private DriverId authenticatedDriverId(final DeliveryMember member) {
        if (member == null) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        return new DriverId(member.getId());
    }
}
