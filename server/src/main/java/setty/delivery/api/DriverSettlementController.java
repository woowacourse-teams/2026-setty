package setty.delivery.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import setty.delivery.auth.domain.DeliveryMember;
import setty.global.auth.LoginDeliveryMember;
import setty.settlement.SettlementQueryService;
import setty.settlement.SettlementsView;

@RestController
@RequiredArgsConstructor
public class DriverSettlementController {

    private final SettlementQueryService settlementQueryService;

    @GetMapping("/api/delivery/settlements")
    public ResponseEntity<SettlementsView> findMySettlements(@LoginDeliveryMember final DeliveryMember member) {
        return ResponseEntity.ok(settlementQueryService.findDriver(member.getId()));
    }
}
