package setty.platform.member.service;

import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.deliveryfee.DeliveryFeeService;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.platform.member.controller.dto.LoginRequest;
import setty.platform.member.controller.dto.LoginResponse;
import setty.platform.member.controller.dto.MemberMeResponse;
import setty.platform.member.controller.dto.SignupRequest;
import setty.platform.member.controller.dto.UpdateDongRequest;
import setty.platform.member.controller.dto.UpdateProfileRequest;
import setty.platform.member.domain.Member;
import setty.platform.member.domain.MemberRole;
import setty.platform.member.repository.MemberRepository;

@Service
public class AuthService {

    private final MemberRepository memberRepository;
    private final DeliveryFeeService deliveryFeeService;
    private final MemberAddressResolver memberAddressResolver;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(
            final MemberRepository memberRepository,
            final DeliveryFeeService deliveryFeeService,
            final MemberAddressResolver memberAddressResolver
    ) {
        this.memberRepository = memberRepository;
        this.deliveryFeeService = deliveryFeeService;
        this.memberAddressResolver = memberAddressResolver;
    }

    @Transactional
    public Member signup(final SignupRequest request) {
        if (memberRepository.existsByLoginId(request.loginId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_ID);
        }
        if (request.dongCode() != null) {
            validateDong(request.dongCode());
        }
        final Member member = new Member(
                request.loginId(),
                passwordEncoder.encode(request.password()),
                MemberRole.MEMBER,
                request.phoneNumber(),
                request.address(),
                request.dongCode()
        );
        memberAddressResolver.resolve(member);
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
        // 탈퇴 직전에 읽은 행에 토큰을 써 넣어 탈퇴를 되살리지 않게, 기본 키로 잠그며 탈퇴 여부를 다시 본다.
        // 아이디 색인으로 잠그면 아이디를 바꾸는 탈퇴와 서로 기다리다 멈추므로 기본 키로만 잠근다.
        if (memberRepository.lockActiveId(member.getId()).isEmpty()) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

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
        lockActive(memberId).clearToken();
    }

    @Transactional
    public MemberMeResponse updateProfile(final Long memberId, final UpdateProfileRequest request) {
        final Member member = lockActive(memberId);
        member.updateContact(request.phoneNumber(), request.address());
        memberAddressResolver.resolve(member);
        return MemberMeResponse.from(member);
    }

    @Transactional
    public MemberMeResponse updateDong(final Long memberId, final UpdateDongRequest request) {
        validateDong(request.dongCode());
        final Member member = lockActive(memberId);
        member.changeDong(request.dongCode());
        return MemberMeResponse.from(member);
    }

    // 탈퇴와 같은 행을 잠가, 탈퇴 직전에 읽은 값으로 탈퇴한 행을 덮어쓰지 않게 한다
    private Member lockActive(final Long memberId) {
        memberRepository.lockActiveId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
        return findMember(memberId);
    }

    private Member findMember(final Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
    }

    private void validateDong(final String dongCode) {
        if (deliveryFeeService.findDong(dongCode).isEmpty()) {
            throw new BusinessException(ErrorCode.DELIVERY_AREA_NOT_SUPPORTED);
        }
    }
}
