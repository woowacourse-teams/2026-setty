package setty.payment.application;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.payment.domain.Payment;
import setty.payment.domain.PaymentRefundReason;
import setty.payment.repository.PaymentRepository;

/**
 * 결제 행의 상태 변경을 짧은 트랜잭션으로 묶는다. 외부 결제 호출(토스)은 이 트랜잭션 밖에서 일어난다.
 * 승인 전 시도 저장, 명확한 실패 뒤 시도 삭제, 승인 확정 저장(필요하면 환불 대상 기록)을 맡는다.
 * 실패 복귀는 기록 없이 이벤트만 발행하므로 여기를 거치지 않는다({@code PaymentService.fail}).
 *
 * <p>주문의 결과는 승인을 기록하기 전에 주문 쪽이 먼저 정한다({@link PaymentApprovalRecorder}). 여기서는 그 결과에 따라 결제만 기록한다.
 *
 * <p>요청 동안 영속성 컨텍스트가 열려 있어(OSIV) 앞서 읽은 결제가 남아 있을 수 있으므로, 잠글 때 DB 값으로 다시 읽는다.
 */
@Component
@RequiredArgsConstructor
public class PaymentRecorder {

    private final PaymentRepository paymentRepository;
    private final PaymentRefundService paymentRefundService;
    private final EntityManager entityManager;
    private final Clock clock;

    /**
     * 토스 승인 전에 시도를 UNKNOWN으로 남긴다. 이미 승인된 주문이면 그 결제를 돌려준다(호출부는 토스를 부르지 않는다).
     * 결과를 모르는 다른 결제 키의 시도가 있거나 같은 순간 다른 요청이 먼저 저장했으면 {@code PAYMENT_IN_PROGRESS}다.
     */
    @Transactional
    public Payment recordAttempt(final Long orderId, final String tossOrderId, final String paymentKey, final int amount) {
        final Optional<Payment> existing = lockByOrderId(orderId);
        if (existing.isEmpty()) {
            try {
                return paymentRepository.saveAndFlush(
                        Payment.attempt(orderId, tossOrderId, paymentKey, amount, clock.instant()));
            } catch (final DataIntegrityViolationException e) {
                throw new BusinessException(ErrorCode.PAYMENT_IN_PROGRESS);
            }
        }
        final Payment payment = existing.get();
        if (!payment.isDone()) {
            payment.retryAttempt(tossOrderId, paymentKey, amount, clock.instant());
        }
        return payment;
    }

    /** 토스가 명확히 거절한 시도를 지워 같은 주문으로 다시 결제할 수 있게 한다. 그사이 바뀐 행은 건드리지 않는다. */
    @Transactional
    public void discardAttempt(final Long orderId, final String paymentKey) {
        lockByOrderId(orderId)
                .filter(payment -> payment.isUnknownAttemptOf(paymentKey))
                .ifPresent(paymentRepository::delete);
    }

    /**
     * 승인 확정 저장. 시도 행이 있으면 DONE으로 전이하고, 없으면 새로 저장한다.
     * 주문이 승인을 받아들이지 않았으면({@code refundReason}이 있으면) 같은 트랜잭션에서 환불 대상도 기록한다.
     * 늦은 승인 응답과 정리 배치가 같은 결제를 함께 기록해도 한 번만 기록한다.
     */
    @Transactional
    public Payment recordCompleted(
            final Long orderId,
            final String tossOrderId,
            final String paymentKey,
            final int amount,
            final LocalDateTime approvedAt,
            final PaymentRefundReason refundReason
    ) {
        final Optional<Payment> existing = lockByOrderId(orderId);
        if (existing.isPresent() && existing.get().isApproved()) {
            if (paymentKey.equals(existing.get().getPaymentKey())) {
                return existing.get();
            }
            throw new BusinessException(ErrorCode.ALREADY_PAID);
        }
        final Payment payment = existing
                .map(attempt -> {
                    attempt.markDone(tossOrderId, paymentKey, approvedAt);
                    return attempt;
                })
                .orElseGet(() -> save(Payment.done(orderId, tossOrderId, paymentKey, amount, approvedAt)));

        if (refundReason != null) {
            paymentRefundService.request(orderId, refundReason);
        }
        return payment;
    }

    // 행이 없을 때 잠금 조회를 하면 MySQL이 빈 구간을 잠가 동시 저장끼리 교착될 수 있으므로, 있는지 먼저 본다.
    private Optional<Payment> lockByOrderId(final Long orderId) {
        final Optional<Payment> payment = paymentRepository.findByOrderId(orderId);
        payment.ifPresent(found -> entityManager.refresh(found, LockModeType.PESSIMISTIC_WRITE));
        return payment;
    }

    private Payment save(final Payment payment) {
        try {
            return paymentRepository.saveAndFlush(payment);
        } catch (final DataIntegrityViolationException e) {
            // 동시 중복 요청 — 이미 다른 트랜잭션이 같은 주문으로 저장했다.
            throw new BusinessException(ErrorCode.ALREADY_PAID);
        }
    }
}
