package setty.deliveryfee.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import setty.deliveryfee.DeliveryFeeService;
import setty.deliveryfee.controller.dto.DongsResponse;

@RestController
@RequiredArgsConstructor
public class DongController {

    private final DeliveryFeeService deliveryFeeService;

    @GetMapping("/api/dongs")
    public ResponseEntity<DongsResponse> dongs() {
        return ResponseEntity.ok(DongsResponse.from(deliveryFeeService.dongs()));
    }
}
