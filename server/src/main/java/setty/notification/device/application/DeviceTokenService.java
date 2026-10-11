package setty.notification.device.application;

import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.notification.device.domain.DevicePlatform;
import setty.notification.device.domain.DeviceToken;
import setty.notification.device.repository.DeviceTokenRepository;

@Service
public class DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;

    public DeviceTokenService(final DeviceTokenRepository deviceTokenRepository) {
        this.deviceTokenRepository = deviceTokenRepository;
    }

    @Transactional
    public void register(final Long memberId, final String token, final DevicePlatform platform) {
        final String normalized = DeviceToken.normalize(token);
        deviceTokenRepository.findByToken(normalized).ifPresentOrElse(
                existing -> existing.reassign(memberId, platform),
                () -> deviceTokenRepository.save(new DeviceToken(memberId, normalized, platform))
        );
    }

    @Transactional(readOnly = true)
    public List<DeviceToken> findAllByMemberIds(final Collection<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return List.of();
        }
        return deviceTokenRepository.findAllByMemberIdIn(memberIds);
    }

    @Transactional
    public void removeInvalid(final Collection<String> tokens) {
        if (tokens.isEmpty()) {
            return;
        }
        deviceTokenRepository.deleteByTokenIn(tokens);
    }

    @Transactional
    public void unregister(final Long memberId, final String token) {
        deviceTokenRepository.findByToken(DeviceToken.normalize(token))
                .filter(existing -> existing.getMemberId().equals(memberId))
                .ifPresent(deviceTokenRepository::delete);
    }
}
