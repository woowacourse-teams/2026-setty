package setty.notification.device.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.notification.device.domain.DevicePlatform;
import setty.notification.device.domain.DeviceToken;
import setty.notification.device.repository.DeviceTokenRepository;
import setty.notification.owner.NotificationOwner;
import setty.notification.owner.OwnerType;

@Service
public class DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;

    public DeviceTokenService(final DeviceTokenRepository deviceTokenRepository) {
        this.deviceTokenRepository = deviceTokenRepository;
    }

    // 한 기기의 주인은 한 명이다. 같은 브라우저로 회원 뒤에 기사로 로그인하면 토큰 주인이 기사로 옮겨 간다.
    @Transactional
    public void register(final NotificationOwner owner, final String token, final DevicePlatform platform) {
        final String normalized = DeviceToken.normalize(token);
        deviceTokenRepository.findByToken(normalized).ifPresentOrElse(
                existing -> existing.reassign(owner, platform),
                () -> deviceTokenRepository.save(new DeviceToken(owner, normalized, platform))
        );
    }

    @Transactional(readOnly = true)
    public List<DeviceToken> findAllByOwners(final Collection<NotificationOwner> owners) {
        final Map<OwnerType, List<Long>> idsByType = owners.stream().collect(Collectors.groupingBy(
                NotificationOwner::type,
                Collectors.mapping(NotificationOwner::id, Collectors.toList())
        ));
        return idsByType.entrySet().stream()
                .flatMap(entry -> deviceTokenRepository
                        .findAllByOwnerTypeAndOwnerIdIn(entry.getKey(), entry.getValue()).stream())
                .toList();
    }

    @Transactional
    public void removeInvalid(final Collection<String> tokens) {
        if (tokens.isEmpty()) {
            return;
        }
        deviceTokenRepository.deleteByTokenIn(tokens);
    }

    @Transactional
    public void unregisterAll(final NotificationOwner owner) {
        deviceTokenRepository.deleteAllByOwner(owner.type(), owner.id());
    }

    @Transactional
    public void unregister(final NotificationOwner owner, final String token) {
        deviceTokenRepository.findByToken(DeviceToken.normalize(token))
                .filter(existing -> existing.isOwnedBy(owner))
                .ifPresent(deviceTokenRepository::delete);
    }
}
