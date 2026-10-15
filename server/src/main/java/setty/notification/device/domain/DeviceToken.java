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

@Entity
@Table(name = "device_tokens")
public class DeviceToken {

    public static final int MAXIMUM_TOKEN_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false, length = MAXIMUM_TOKEN_LENGTH)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DevicePlatform platform;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DeviceToken() {
    }

    public DeviceToken(final Long memberId, final String token, final DevicePlatform platform) {
        if (memberId == null || platform == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        this.memberId = memberId;
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

    public void reassign(final Long memberId, final DevicePlatform platform) {
        this.memberId = memberId;
        this.platform = platform;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
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
