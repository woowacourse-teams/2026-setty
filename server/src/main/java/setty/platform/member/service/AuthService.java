package setty.platform.member.service;

import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.address.AddressResolution;
import setty.address.AddressService;
import setty.address.StandardAddress;
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
import setty.platform.member.domain.ResolvedAddress;
import setty.platform.member.repository.MemberRepository;

@Service
public class AuthService {

    private final MemberRepository memberRepository;
    private final DeliveryFeeService deliveryFeeService;
    private final AddressService addressService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(
            final MemberRepository memberRepository,
            final DeliveryFeeService deliveryFeeService,
            final AddressService addressService
    ) {
        this.memberRepository = memberRepository;
        this.deliveryFeeService = deliveryFeeService;
        this.addressService = addressService;
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
        resolveAddress(member);
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
        resolveAddress(member);
        return MemberMeResponse.from(member);
    }

    @Transactional
    public MemberMeResponse updateDong(final Long memberId, final UpdateDongRequest request) {
        validateDong(request.dongCode());
        final Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
        member.changeDong(request.dongCode());
        return MemberMeResponse.from(member);
    }

    // 해석에 실패해도 가입·수정은 그대로 진행하고 상태만 남긴다. 회원이 고른 행정동은 덮어쓰지 않는다.
    private void resolveAddress(final Member member) {
        final AddressResolution resolution = addressService.resolve(member.getAddress());
        member.changeResolvedAddress(ResolvedAddress.from(resolution));
        final StandardAddress address = resolution.address();
        if (member.getDongCode() == null && address != null && address.dongCode() != null
                && deliveryFeeService.findDong(address.dongCode()).isPresent()) {
            member.changeDong(address.dongCode());
        }
    }

    private void validateDong(final String dongCode) {
        if (deliveryFeeService.findDong(dongCode).isEmpty()) {
            throw new BusinessException(ErrorCode.DELIVERY_AREA_NOT_SUPPORTED);
        }
    }
}
