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
import java.time.LocalDateTime;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

/**
 * 결제 결과를 주문당 1건으로 저장한다.
 *
 * <p>주문은 결제 이전에 PENDING 상태로 먼저 존재하므로 {@code order_id}는 항상 확정된 주문을 가리킨다.
 * 토스 승인을 부르기 전에 {@link #attempt}로 UNKNOWN 행을 남기고, 승인이 확인되면 같은 행을 {@link #markDone}으로 DONE 전이한다.
 * 레거시 실패(ABORTED) 행에는 승인 키·승인 시각이 없으며, 재시도하면 같은 행을 다시 시도로 바꾼다(주문당 1행 유지).
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "payment_key", length = 200)
    private String paymentKey;

    @Column(name = "toss_order_id", nullable = false, length = 64)
    private String tossOrderId;

    @Column(name = "amount", nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "attempted_at")
    private Instant attemptedAt;

    protected Payment() {
    }

    private Payment(
            final Long orderId,
            final String tossOrderId,
            final int amount,
            final PaymentStatus status,
            final String paymentKey,
            final LocalDateTime approvedAt,
            final Instant attemptedAt
    ) {
        this.orderId = orderId;
        this.tossOrderId = tossOrderId;
        this.amount = amount;
        this.status = status;
        this.paymentKey = paymentKey;
        this.approvedAt = approvedAt;
        this.attemptedAt = attemptedAt;
    }

    public static Payment attempt(
            final Long orderId,
            final String tossOrderId,
            final String paymentKey,
            final int amount,
            final Instant attemptedAt
    ) {
        return new Payment(orderId, tossOrderId, amount, PaymentStatus.UNKNOWN, paymentKey, null, attemptedAt);
    }

    public static Payment done(
            final Long orderId,
            final String tossOrderId,
            final String paymentKey,
            final int amount,
            final LocalDateTime approvedAt
    ) {
        return new Payment(orderId, tossOrderId, amount, PaymentStatus.DONE, paymentKey, approvedAt, null);
    }

    /**
     * 같은 주문의 승인을 다시 시도한다. 같은 결제 키의 UNKNOWN은 토스 멱등키가 같아 다시 불러도 안전하다.
     * 다른 결제 키로 결과를 모르는 시도를 덮으면 앞선 승인을 찾을 길이 없어지므로 막는다.
     */
    public void retryAttempt(final String tossOrderId, final String paymentKey, final int amount, final Instant now) {
        if (status == PaymentStatus.UNKNOWN && !paymentKey.equals(this.paymentKey)) {
            throw new BusinessException(ErrorCode.PAYMENT_IN_PROGRESS);
        }
        if (status != PaymentStatus.UNKNOWN && status != PaymentStatus.ABORTED) {
            throw new BusinessException(ErrorCode.ALREADY_PAID);
        }
        this.tossOrderId = tossOrderId;
        this.paymentKey = paymentKey;
        this.amount = amount;
        this.attemptedAt = now;
        this.status = PaymentStatus.UNKNOWN;
    }

    /**
     * 승인 시도(UNKNOWN)나 레거시 ABORTED 행을 DONE으로 전이한다. 이미 DONE이면 중복 승인이므로 막는다.
     * 재시도는 새 토스 orderId로 오므로 실제 승인에 쓰인 값으로 갱신한다.
     */
    public void markDone(final String tossOrderId, final String paymentKey, final LocalDateTime approvedAt) {
        if (this.status == PaymentStatus.DONE || this.status == PaymentStatus.CANCELED) {
            throw new BusinessException(ErrorCode.ALREADY_PAID);
        }
        this.tossOrderId = tossOrderId;
        this.paymentKey = paymentKey;
        this.approvedAt = approvedAt;
        this.status = PaymentStatus.DONE;
    }

    public void markCanceled() {
        if (this.status != PaymentStatus.DONE) {
            throw new IllegalStateException("승인된 결제만 환불 완료로 바꿀 수 있습니다. paymentId=" + id);
        }
        this.status = PaymentStatus.CANCELED;
    }

    public boolean isDone() {
        return this.status == PaymentStatus.DONE;
    }

    public boolean isApproved() {
        return this.status == PaymentStatus.DONE || this.status == PaymentStatus.CANCELED;
    }

    public boolean isUnknown() {
        return this.status == PaymentStatus.UNKNOWN;
    }

    public boolean isUnknownAttemptOf(final String paymentKey) {
        return this.status == PaymentStatus.UNKNOWN && paymentKey.equals(this.paymentKey);
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getPaymentKey() {
        return paymentKey;
    }

    public String getTossOrderId() {
        return tossOrderId;
    }

    public int getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }
}
