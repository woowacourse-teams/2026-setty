package setty.delivery.auth.service;

import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.delivery.auth.controller.dto.LoginRequest;
import setty.delivery.auth.controller.dto.LoginResponse;
import setty.delivery.auth.controller.dto.SignupRequest;
import setty.delivery.auth.domain.DeliveryMember;
import setty.delivery.auth.repository.DeliveryMemberRepository;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

@Service
public class DeliveryAuthService {

    private final DeliveryMemberRepository deliveryMemberRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public DeliveryAuthService(final DeliveryMemberRepository deliveryMemberRepository) {
        this.deliveryMemberRepository = deliveryMemberRepository;
    }

    @Transactional
    public DeliveryMember signup(final SignupRequest request) {
        if (deliveryMemberRepository.existsByLoginId(request.loginId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_ID);
        }
        final DeliveryMember member = new DeliveryMember(
                request.loginId(),
                passwordEncoder.encode(request.password()),
                request.phoneNumber(),
                request.licensePlateNumber(),
                request.carType(),
                request.businessRegistrationNumber()
        );
        try {
            return deliveryMemberRepository.saveAndFlush(member);
        } catch (final DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_ID);
        }
    }

    @Transactional
    public LoginResponse login(final LoginRequest request) {
        final DeliveryMember member = deliveryMemberRepository.findByLoginId(request.loginId())
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));

        if (!member.matchPassword(passwordEncoder, request.password())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        final String token = UUID.randomUUID().toString();
        member.rotateToken(token);
        return new LoginResponse(token);
    }
}
