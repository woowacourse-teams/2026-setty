package setty.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * 승인된 결제 한 건의 환불 진행. 토스 결제 취소는 응답을 못 받을 수 있으므로 같은 멱등키로 다시 보낸다.
 * 결제와 생명주기가 달라 {@code payments}와 나눠 둔다.
 */
@Entity
@Table(name = "payment_refunds")
public class PaymentRefund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 20)
    private PaymentRefundReason reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private PaymentRefundStatus status;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error_code", length = 60)
    private String lastErrorCode;

    @Column(name = "transaction_key", length = 64)
    private String transactionKey;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected PaymentRefund() {
    }

    private PaymentRefund(final Long paymentId, final PaymentRefundReason reason, final Instant requestedAt) {
        this.paymentId = paymentId;
        this.reason = reason;
        this.status = PaymentRefundStatus.REQUESTED;
        this.idempotencyKey = UUID.randomUUID().toString();
        this.requestedAt = requestedAt;
    }

    public static PaymentRefund requested(final Long paymentId, final PaymentRefundReason reason, final Instant now) {
        return new PaymentRefund(paymentId, reason, now);
    }

    public void complete(final String transactionKey, final Instant canceledAt, final Instant now) {
        attempts++;
        this.transactionKey = transactionKey;
        this.canceledAt = canceledAt;
        this.status = PaymentRefundStatus.DONE;
        this.completedAt = now;
    }

    public void fail(final String errorCode, final Instant now) {
        attempts++;
        this.lastErrorCode = errorCode;
        this.status = PaymentRefundStatus.FAILED;
        this.completedAt = now;
    }

    /** 다음 배치에서 다시 보낼 실패. 한도에 닿으면 멈춘다. 멈췄으면 true. */
    public boolean retryLater(final String errorCode, final int maxAttempts, final Instant now) {
        if (attempts + 1 >= maxAttempts) {
            fail(errorCode, now);
            return true;
        }
        attempts++;
        this.lastErrorCode = errorCode;
        return false;
    }

    public boolean isRequested() {
        return status == PaymentRefundStatus.REQUESTED;
    }

    public Long getId() {
        return id;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public PaymentRefundReason getReason() {
        return reason;
    }

    public PaymentRefundStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }
}
