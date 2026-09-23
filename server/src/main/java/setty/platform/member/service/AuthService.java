package setty.platform.member.service;

import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.platform.member.controller.dto.LoginRequest;
import setty.platform.member.controller.dto.LoginResponse;
import setty.platform.member.controller.dto.MemberMeResponse;
import setty.platform.member.controller.dto.SignupRequest;
import setty.platform.member.controller.dto.UpdateProfileRequest;
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
                MemberRole.MEMBER,
                request.phoneNumber(),
                request.address()
        );
        try {
            return memberRepository.saveAndFlush(member);
        } catch (final DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_ID);
        }
    }

    @Transactional
    public LoginResponse login(final LoginRequest request) {
        final Member member = memberRepository.findByLoginId(request.loginId())
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));

        if (!member.matchPassword(passwordEncoder, request.password())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        final String token = UUID.randomUUID().toString();
        member.rotateToken(token);
        return new LoginResponse(token, member.getRole().name());
    }

    @Transactional(readOnly = true)
    public Member findByToken(final String token) {
        return memberRepository.findByToken(token)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
    }

    @Transactional
    public void logout(final Long memberId) {
        final Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
        member.clearToken();
    }

    @Transactional
    public MemberMeResponse updateProfile(final Long memberId, final UpdateProfileRequest request) {
        final Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
        member.updateContact(request.phoneNumber(), request.address());
        return MemberMeResponse.from(member);
    }
}
