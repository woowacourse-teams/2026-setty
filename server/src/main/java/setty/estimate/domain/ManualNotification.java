package setty.estimate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "manual_notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ManualNotification {
    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "estimate_request_id", nullable = false, unique = true)
    private Long estimateRequestId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String messageContent;

    @Column(nullable = false)
    private boolean transportFeasible;

    @Column(nullable = false)
    private LocalDateTime notifiedAt;

    private ManualNotification(
            final Long estimateRequestId,
            final String messageContent,
            final boolean transportFeasible
    ) {
        this.estimateRequestId = estimateRequestId;
        this.messageContent = messageContent;
        this.transportFeasible = transportFeasible;
        this.notifiedAt = LocalDateTime.now(SEOUL_ZONE_ID);
    }

    public static ManualNotification create(
            final Long estimateRequestId,
            final String messageContent,
            final boolean transportFeasible
    ) {
        return new ManualNotification(estimateRequestId, messageContent, transportFeasible);
    }

    public void update(final String messageContent, final boolean transportFeasible) {
        this.messageContent = messageContent;
        this.transportFeasible = transportFeasible;
    }
}
