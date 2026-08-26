package setty.delivery.auth.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import setty.delivery.auth.controller.dto.LoginRequest;
import setty.delivery.auth.controller.dto.LoginResponse;
import setty.delivery.auth.controller.dto.SignupRequest;
import setty.delivery.auth.controller.dto.SignupResponse;
import setty.delivery.auth.service.DeliveryAuthService;

@RestController
@RequestMapping("/api/delivery/auth")
public class DeliveryAuthController {

    private final DeliveryAuthService deliveryAuthService;

    public DeliveryAuthController(final DeliveryAuthService deliveryAuthService) {
        this.deliveryAuthService = deliveryAuthService;
    }

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody final SignupRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(SignupResponse.from(deliveryAuthService.signup(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody final LoginRequest request) {
        return ResponseEntity.ok(deliveryAuthService.login(request));
    }
}
