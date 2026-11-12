package setty.notification.device.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.notification.owner.NotificationOwner;
import setty.notification.owner.OwnerType;

@Entity
@Table(name = "device_tokens")
public class DeviceToken {

    public static final int MAXIMUM_TOKEN_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 10)
    private OwnerType ownerType;

    // 주인 종류에 따라 회원 ID 또는 기사 ID다. 배포 중 옛 버전과 같이 쓰려고 칸 이름은 member_id 그대로 둔다.
    @Column(name = "member_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false, length = MAXIMUM_TOKEN_LENGTH)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DevicePlatform platform;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DeviceToken() {
    }

    public DeviceToken(final NotificationOwner owner, final String token, final DevicePlatform platform) {
        if (owner == null || platform == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        this.ownerType = owner.type();
        this.ownerId = owner.id();
        this.token = normalize(token);
        this.platform = platform;
        this.updatedAt = Instant.now();
    }

    public static String normalize(final String token) {
        if (token == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        final String normalized = token.strip();
        if (normalized.isEmpty() || normalized.length() > MAXIMUM_TOKEN_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return normalized;
    }

    public void reassign(final NotificationOwner owner, final DevicePlatform platform) {
        this.ownerType = owner.type();
        this.ownerId = owner.id();
        this.platform = platform;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public NotificationOwner getOwner() {
        return new NotificationOwner(ownerType, ownerId);
    }

    public boolean isOwnedBy(final NotificationOwner owner) {
        return getOwner().equals(owner);
    }

    public String getToken() {
        return token;
    }

    public DevicePlatform getPlatform() {
        return platform;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
