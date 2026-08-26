package setty.delivery.api;

import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import setty.delivery.application.AcceptDeliveryService;
import setty.delivery.application.CompleteDeliveryService;
import setty.delivery.application.DeliveryQueryService;
import setty.delivery.application.PickupDeliveryService;
import setty.delivery.auth.domain.DeliveryMember;
import setty.delivery.api.dto.DeliveryRequestDetailResponse;
import setty.delivery.api.dto.DeliveryRequestSummaryResponse;
import setty.delivery.api.dto.ShipmentDetailResponse;
import setty.delivery.api.dto.ShipmentSummaryResponse;
import setty.delivery.domain.DeliveryId;
import setty.delivery.domain.DriverId;
import setty.global.auth.LoginDeliveryMember;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

@RestController
@RequestMapping("/api/delivery")
public class DeliveryController {

    private final DeliveryQueryService deliveryQueryService;
    private final AcceptDeliveryService acceptDeliveryService;
    private final PickupDeliveryService pickupDeliveryService;
    private final CompleteDeliveryService completeDeliveryService;

    public DeliveryController(
            final DeliveryQueryService deliveryQueryService,
            final AcceptDeliveryService acceptDeliveryService,
            final PickupDeliveryService pickupDeliveryService,
            final CompleteDeliveryService completeDeliveryService
    ) {
        this.deliveryQueryService = deliveryQueryService;
        this.acceptDeliveryService = acceptDeliveryService;
        this.pickupDeliveryService = pickupDeliveryService;
        this.completeDeliveryService = completeDeliveryService;
    }

    @GetMapping("/requests")
    public ResponseEntity<List<DeliveryRequestSummaryResponse>> findRequests(
            @LoginDeliveryMember final DeliveryMember member
    ) {
        authenticatedDriverId(member);
        final List<DeliveryRequestSummaryResponse> response = deliveryQueryService.findAvailableRequests().stream()
                .map(DeliveryRequestSummaryResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/requests/{deliveryId}")
    public ResponseEntity<DeliveryRequestDetailResponse> findRequest(
            @LoginDeliveryMember final DeliveryMember member,
            @PathVariable final long deliveryId
    ) {
        authenticatedDriverId(member);
        return ResponseEntity.ok(DeliveryRequestDetailResponse.from(
                deliveryQueryService.findAvailableRequest(new DeliveryId(deliveryId))
        ));
    }

    @PostMapping("/requests/{deliveryId}/acceptance")
    public ResponseEntity<Void> accept(
            @LoginDeliveryMember final DeliveryMember member,
            @PathVariable final long deliveryId
    ) {
        acceptDeliveryService.accept(
                new DeliveryId(deliveryId),
                authenticatedDriverId(member),
                Instant.now()
        );
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/shipments")
    public ResponseEntity<List<ShipmentSummaryResponse>> findShipments(
            @LoginDeliveryMember final DeliveryMember member
    ) {
        final List<ShipmentSummaryResponse> response = deliveryQueryService
                .findShipments(authenticatedDriverId(member)).stream()
                .map(ShipmentSummaryResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/shipments/{deliveryId}")
    public ResponseEntity<ShipmentDetailResponse> findShipment(
            @LoginDeliveryMember final DeliveryMember member,
            @PathVariable final long deliveryId
    ) {
        return ResponseEntity.ok(ShipmentDetailResponse.from(
                deliveryQueryService.findShipment(
                        new DeliveryId(deliveryId),
                        authenticatedDriverId(member)
                )
        ));
    }

    @PostMapping("/shipments/{deliveryId}/pickup")
    public ResponseEntity<Void> pickUp(
            @LoginDeliveryMember final DeliveryMember member,
            @PathVariable final long deliveryId
    ) {
        pickupDeliveryService.pickUp(
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
        completeDeliveryService.complete(
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
