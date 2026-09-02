package setty.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

/**
 * 결제 결과를 주문당 1건으로 저장한다.
 *
 * <p>주문은 결제 이전에 PENDING 상태로 먼저 존재하므로 {@code order_id}는 항상 확정된 주문을 가리킨다.
 * 실패(ABORTED) 저장 시에는 승인 키·승인 시각이 없으므로 {@code payment_key}·{@code approved_at}은 null이다.
 * 실패 후 재시도로 승인되면 같은 행을 {@link #markDone}으로 DONE 전이한다(주문당 1행 유지).
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

    protected Payment() {
    }

    private Payment(
            final Long orderId,
            final String tossOrderId,
            final int amount,
            final PaymentStatus status,
            final String paymentKey,
            final LocalDateTime approvedAt
    ) {
        this.orderId = orderId;
        this.tossOrderId = tossOrderId;
        this.amount = amount;
        this.status = status;
        this.paymentKey = paymentKey;
        this.approvedAt = approvedAt;
    }

    public static Payment done(
            final Long orderId,
            final String tossOrderId,
            final String paymentKey,
            final int amount,
            final LocalDateTime approvedAt
    ) {
        return new Payment(orderId, tossOrderId, amount, PaymentStatus.DONE, paymentKey, approvedAt);
    }

    /**
     * 기존 행(레거시 ABORTED)을 재시도 승인 시 DONE으로 전이한다. 이미 DONE이면 중복 승인이므로 막는다.
     * 재시도는 새 토스 orderId로 오므로 실제 승인에 쓰인 값으로 갱신한다.
     */
    public void markDone(final String tossOrderId, final String paymentKey, final LocalDateTime approvedAt) {
        if (this.status == PaymentStatus.DONE) {
            throw new BusinessException(ErrorCode.ALREADY_PAID);
        }
        this.tossOrderId = tossOrderId;
        this.paymentKey = paymentKey;
        this.approvedAt = approvedAt;
        this.status = PaymentStatus.DONE;
    }

    public boolean isDone() {
        return this.status == PaymentStatus.DONE;
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
}
