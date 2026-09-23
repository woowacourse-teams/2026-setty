package setty.delivery.auth.application;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.delivery.auth.domain.DeliveryMember;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

@Service
@RequiredArgsConstructor
public class DeliveryAuthService {

    private final DeliveryMemberRepository deliveryMemberRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Transactional
    public SignupResult signup(final SignupCommand command) {
        if (deliveryMemberRepository.existsByLoginId(command.loginId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_ID);
        }
        final DeliveryMember member = new DeliveryMember(
                command.loginId(),
                passwordEncoder.encode(command.password()),
                command.phoneNumber(),
                command.licensePlateNumber(),
                command.carType(),
                command.businessRegistrationNumber()
        );
        try {
            final DeliveryMember savedMember = deliveryMemberRepository.save(member);
            return new SignupResult(savedMember.getId(), savedMember.getLoginId());
        } catch (final DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_ID);
        }
    }

    @Transactional
    public LoginResult login(final LoginCommand command) {
        final DeliveryMember member = deliveryMemberRepository.findByLoginId(command.loginId())
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));

        if (!member.matchPassword(passwordEncoder, command.password())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        final String token = UUID.randomUUID().toString();
        member.rotateToken(token);
        return new LoginResult(token);
    }
}
