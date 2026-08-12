package setty.dispatch.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import setty.dispatch.dto.seller.SellerInputSessionResponse;
import setty.dispatch.dto.seller.SellerInputSubmitRequest;
import setty.dispatch.service.SellerInputService;

@RestController
@RequestMapping("/api/dispatch-requests/seller-sessions")
public class SellerInputController {
    private final SellerInputService sellerInputService;

    public SellerInputController(final SellerInputService sellerInputService) {
        this.sellerInputService = sellerInputService;
    }

    @GetMapping("/{token}")
    public ResponseEntity<SellerInputSessionResponse> findSession(@PathVariable final String token) {
        return ResponseEntity.ok(sellerInputService.findSession(token));
    }

    @PostMapping("/{token}")
    public ResponseEntity<Void> submit(
            @PathVariable final String token,
            @Valid @RequestBody final SellerInputSubmitRequest request
    ) {
        sellerInputService.submit(token, request);

        return ResponseEntity.noContent().build();
    }
}
