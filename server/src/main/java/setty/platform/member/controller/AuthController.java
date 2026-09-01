package setty.platform.member.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import setty.global.auth.LoginMember;
import setty.platform.member.controller.dto.LoginRequest;
import setty.platform.member.controller.dto.LoginResponse;
import setty.platform.member.controller.dto.MemberMeResponse;
import setty.platform.member.controller.dto.SignupRequest;
import setty.platform.member.controller.dto.SignupResponse;
import setty.platform.member.controller.dto.UpdateProfileRequest;
import setty.platform.member.domain.Member;
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

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody final LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<MemberMeResponse> me(@LoginMember final Member member) {
        return ResponseEntity.ok(MemberMeResponse.from(member));
    }

    @PutMapping("/me")
    public ResponseEntity<MemberMeResponse> updateProfile(
            @LoginMember final Member member,
            @Valid @RequestBody final UpdateProfileRequest request
    ) {
        return ResponseEntity.ok(authService.updateProfile(member.getId(), request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@LoginMember final Member member) {
        authService.logout(member.getId());
        return ResponseEntity.noContent().build();
    }
}
