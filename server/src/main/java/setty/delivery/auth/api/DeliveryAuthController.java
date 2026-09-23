package setty.delivery.auth.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import setty.delivery.auth.api.dto.LoginRequest;
import setty.delivery.auth.api.dto.LoginResponse;
import setty.delivery.auth.api.dto.SignupRequest;
import setty.delivery.auth.api.dto.SignupResponse;
import setty.delivery.auth.application.DeliveryAuthService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/delivery/auth")
public class DeliveryAuthController {

    private final DeliveryAuthService deliveryAuthService;

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody final SignupRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(SignupResponse.from(deliveryAuthService.signup(request.toCommand())));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody final LoginRequest request) {
        return ResponseEntity.ok(LoginResponse.from(deliveryAuthService.login(request.toCommand())));
    }
}
