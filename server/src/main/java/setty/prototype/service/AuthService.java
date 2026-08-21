package setty.prototype.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.prototype.domain.Member;
import setty.prototype.dto.auth.LoginRequest;
import setty.prototype.exception.InvalidCredentialsException;
import setty.prototype.repository.MemberRepository;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            final MemberRepository memberRepository,
            final PasswordEncoder passwordEncoder
    ) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 가입과 로그인을 한 요청으로 처리한다.
     * 처음 보는 휴대폰 번호면 요청에 담긴 비밀번호로 회원을 만들고 바로 로그인시킨다.
     */
    @Transactional
    public AuthenticatedMember logIn(final LoginRequest request) {
        return memberRepository.findByPhoneNumber(request.phoneNumber())
                .map(member -> logInExisting(member, request.password()))
                .orElseGet(() -> signUp(request));
    }

    private AuthenticatedMember logInExisting(final Member member, final String password) {
        if (!passwordEncoder.matches(password, member.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        log.info("프로토타입 로그인 완료. memberId={}", member.getId());

        return AuthenticatedMember.loggedIn(member);
    }

    private AuthenticatedMember signUp(final LoginRequest request) {
        final Member member = memberRepository.save(
                new Member(request.phoneNumber(), passwordEncoder.encode(request.password()))
        );

        log.info("프로토타입 회원 가입과 로그인 완료. memberId={}", member.getId());

        return AuthenticatedMember.signedUp(member);
    }
}
