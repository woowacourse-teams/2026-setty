package setty.platform.member.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import setty.global.auth.LoginMember;
import setty.settlement.SettlementQueryService;
import setty.settlement.SettlementsView;

@RestController
@RequiredArgsConstructor
public class MySettlementController {

    private final SettlementQueryService settlementQueryService;

    @GetMapping("/api/me/settlements")
    public ResponseEntity<SettlementsView> findMySettlements(@LoginMember final Long memberId) {
        return ResponseEntity.ok(settlementQueryService.findSeller(memberId));
    }
}
