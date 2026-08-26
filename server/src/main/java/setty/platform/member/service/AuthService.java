package setty.platform.member.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.platform.member.controller.dto.SignupRequest;
import setty.platform.member.domain.Member;
import setty.platform.member.domain.MemberRole;
import setty.platform.member.repository.MemberRepository;

@Service
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(final MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Transactional
    public Member signup(final SignupRequest request) {
        if (memberRepository.existsByLoginId(request.loginId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_ID);
        }
        final Member member = new Member(
                request.loginId(),
                passwordEncoder.encode(request.password()),
                MemberRole.valueOf(request.role()),
                request.phoneNumber(),
                request.address()
        );
        try {
            // 동시 가입은 login_id UNIQUE가 막는다. flush를 당겨 위반을 여기서 잡아 400으로 변환한다.
            return memberRepository.saveAndFlush(member);
        } catch (final DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_ID);
        }
    }
}
