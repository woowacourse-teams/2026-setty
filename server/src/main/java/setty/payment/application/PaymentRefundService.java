package setty.payment.application;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.payment.domain.Payment;
import setty.payment.domain.PaymentRefund;
import setty.payment.domain.PaymentRefundReason;
import setty.payment.infrastructure.TossCancelResult;
import setty.payment.repository.PaymentRefundRepository;
import setty.payment.repository.PaymentRepository;

/**
 * 환불 대상 기록과 토스 결제 취소 결과 기록을 짧은 트랜잭션으로 묶는다. 토스 호출은 {@link PaymentRefundProcessor}가 트랜잭션 밖에서 한다.
 * 두 프로세스가 같은 환불을 함께 처리해도 결과는 행을 잠근 뒤 아직 진행 중일 때만 기록한다.
 */
@Service
@RequiredArgsConstructor
public class PaymentRefundService {

    private static final Logger log = LoggerFactory.getLogger(PaymentRefundService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentRefundRepository paymentRefundRepository;
    private final PaymentRecoveryProperties properties;
    private final EntityManager entityManager;
    private final Clock clock;

    /**
     * 승인된 결제를 환불 대상으로 기록한다. 같은 결제는 한 번만 기록한다(이벤트 재발행에도 안전).
     * 같은 요청이 동시에 들어오면 유일 키에 걸린 쪽이 실패하고, 발행 기록 재발행 때 이미 있는 행을 보고 끝난다.
     */
    @Transactional
    public void request(final Long orderId, final PaymentRefundReason reason) {
        final Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
        if (payment == null || !payment.isApproved()) {
            log.error("환불을 요청받았지만 승인된 결제가 없습니다. 수동 확인이 필요합니다. orderId={}, paymentStatus={}",
                    orderId, payment == null ? null : payment.getStatus());
            return;
        }
        if (paymentRefundRepository.existsByPaymentId(payment.getId())) {
            return;
        }
        paymentRefundRepository.save(PaymentRefund.requested(payment.getId(), reason, clock.instant()));
        log.warn("끝난 주문의 승인 결제를 환불 대상으로 기록했습니다. orderId={}, reason={}", orderId, reason);
    }

    @Transactional
    public void record(final Long refundId, final TossCancelResult result) {
        final PaymentRefund refund = paymentRefundRepository.findByIdForUpdate(refundId).orElseThrow();
        if (!refund.isRequested()) {
            return;
        }
        final Payment payment = paymentRepository.findById(refund.getPaymentId()).orElseThrow();
        entityManager.refresh(payment, LockModeType.PESSIMISTIC_WRITE);
        switch (result) {
            case TossCancelResult.Canceled canceled when canceled.isFullyCanceled(payment.getAmount()) -> {
                refund.complete(canceled.lastTransactionKey(), canceled.lastCanceledAt(), clock.instant());
                payment.markCanceled();
            }
            case TossCancelResult.Canceled canceled -> stop(refund, payment, "CANCEL_AMOUNT_MISMATCH");
            case TossCancelResult.AlreadyCanceled alreadyCanceled -> {
                refund.complete(null, null, clock.instant());
                payment.markCanceled();
            }
            case TossCancelResult.Retryable retryable -> {
                if (refund.retryLater(retryable.errorCode(), properties.maxRefundAttempts(), clock.instant())) {
                    alert(refund, payment);
                }
            }
            case TossCancelResult.Rejected rejected -> stop(refund, payment, rejected.errorCode());
        }
    }

    private void stop(final PaymentRefund refund, final Payment payment, final String errorCode) {
        refund.fail(errorCode, clock.instant());
        alert(refund, payment);
    }

    private void alert(final PaymentRefund refund, final Payment payment) {
        log.error("토스 결제 취소에 실패해 환불을 멈췄습니다. 수동 환불 확인이 필요합니다. orderId={}, refundId={}, errorCode={}, attempts={}",
                payment.getOrderId(), refund.getId(), refund.getLastErrorCode(), refund.getAttempts());
    }
}
