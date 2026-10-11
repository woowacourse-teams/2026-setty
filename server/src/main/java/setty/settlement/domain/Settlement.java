package setty.settlement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

@Getter
@Entity
@Table(name = "settlements")
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payee_type", nullable = false, length = 20, updatable = false)
    private PayeeType payeeType;

    @Column(name = "payee_id", nullable = false, updatable = false)
    private Long payeeId;

    @Column(name = "listing_id", updatable = false)
    private Long listingId;

    @Column(name = "amount", nullable = false, updatable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SettlementStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    protected Settlement() {
    }

    private Settlement(
            final Long orderId,
            final PayeeType payeeType,
            final Long payeeId,
            final Long listingId,
            final int amount,
            final Instant createdAt
    ) {
        if (!isPositive(orderId) || !isPositive(payeeId) || amount < 0 || createdAt == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        this.orderId = orderId;
        this.payeeType = payeeType;
        this.payeeId = payeeId;
        this.listingId = listingId;
        this.amount = amount;
        this.status = SettlementStatus.PENDING;
        this.createdAt = createdAt;
    }

    public static Settlement seller(
            final Long orderId,
            final Long sellerId,
            final Long listingId,
            final int itemPrice,
            final Instant createdAt
    ) {
        if (!isPositive(listingId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return new Settlement(orderId, PayeeType.SELLER, sellerId, listingId, itemPrice, createdAt);
    }

    public static Settlement driver(
            final Long orderId,
            final Long driverId,
            final int deliveryFee,
            final Instant createdAt
    ) {
        return new Settlement(orderId, PayeeType.DRIVER, driverId, null, deliveryFee, createdAt);
    }

    // 결론이 난 정산은 바꾸지 않는다. 재발행된 결론 이벤트는 같은 결론이므로 무시해도 된다.
    public void apply(final SettlementDecision decision) {
        if (status != SettlementStatus.PENDING) {
            return;
        }
        status = decision.status();
        if (status == SettlementStatus.CONFIRMED) {
            confirmedAt = decision.decidedAt();
            return;
        }
        cancelledAt = decision.decidedAt();
    }

    private static boolean isPositive(final Long value) {
        return value != null && value > 0;
    }
}
