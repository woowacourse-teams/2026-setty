package setty.prototype.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import setty.prototype.dto.auth.AuthMemberResponse;
import setty.prototype.dto.auth.LoginRequest;
import setty.prototype.service.AuthService;
import setty.prototype.service.AuthenticatedMember;
import setty.prototype.web.LoginMemberId;
import setty.prototype.web.LoginSession;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    public AuthController(final AuthService authService) {
        this.authService = authService;
    }

    /**
     * 처음 보는 휴대폰 번호면 가입 후 로그인하고 {@code 201}, 이미 가입한 번호면 {@code 200}을 돌려준다.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthMemberResponse> logIn(
            @Valid @RequestBody final LoginRequest request,
            final HttpServletRequest servletRequest
    ) {
        final AuthenticatedMember member = authService.logIn(request);
        LoginSession.start(servletRequest, member.id());

        return ResponseEntity.status(member.newMember() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(new AuthMemberResponse(member.phoneNumber()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logOut(
            @LoginMemberId final Long memberId,
            final HttpServletRequest servletRequest
    ) {
        LoginSession.end(servletRequest);

        log.info("프로토타입 로그아웃 완료. memberId={}", memberId);

        return ResponseEntity.noContent().build();
    }
}
