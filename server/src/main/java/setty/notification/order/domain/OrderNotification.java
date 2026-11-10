package setty.notification.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import setty.notification.listing.domain.NotificationStatus;

@Entity
@Table(name = "order_notifications")
public class OrderNotification {

    public static final int MAXIMUM_ATTEMPTS = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OrderNotificationRecipient recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private OrderNotificationKind kind;

    @Column
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NotificationStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected OrderNotification() {
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getListingId() {
        return listingId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public OrderNotificationRecipient getRecipient() {
        return recipient;
    }

    public OrderNotificationKind getKind() {
        return kind;
    }

    public Integer getAmount() {
        return amount;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isRead() {
        return readAt != null;
    }
}
