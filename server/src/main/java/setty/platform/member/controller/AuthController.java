package setty.platform.member.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import setty.platform.member.controller.dto.SignupRequest;
import setty.platform.member.controller.dto.SignupResponse;
import setty.platform.member.service.AuthService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(final AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody final SignupRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(SignupResponse.from(authService.signup(request)));
    }
}
