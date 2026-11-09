package setty.payment.application;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import setty.payment.domain.Payment;
import setty.payment.domain.PaymentRefund;
import setty.payment.domain.PaymentRefundStatus;
import setty.payment.infrastructure.TossCancelResult;
import setty.payment.infrastructure.TossPaymentClient;
import setty.payment.repository.PaymentRefundRepository;
import setty.payment.repository.PaymentRepository;

/** 기록된 환불 대상을 토스 결제 취소로 처리한다. 토스 응답을 기다리는 동안 DB 트랜잭션을 열어 두지 않는다. */
@Component
@RequiredArgsConstructor
public class PaymentRefundProcessor {

    private static final Logger log = LoggerFactory.getLogger(PaymentRefundProcessor.class);

    private final PaymentRefundRepository paymentRefundRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentRefundService paymentRefundService;
    private final TossPaymentClient tossPaymentClient;
    private final PaymentRecoveryProperties properties;

    public void processRequested() {
        final List<PaymentRefund> requested = paymentRefundRepository.findByStatusOrderByIdAsc(
                PaymentRefundStatus.REQUESTED, Limit.of(properties.batchSize()));
        for (final PaymentRefund refund : requested) {
            try {
                process(refund);
            } catch (final RuntimeException exception) {
                log.warn("환불 처리 중 오류가 나 다음 배치에서 다시 시도합니다. refundId={}", refund.getId(), exception);
            }
        }
    }

    private void process(final PaymentRefund refund) {
        final Payment payment = paymentRepository.findById(refund.getPaymentId()).orElseThrow();
        final TossCancelResult result = tossPaymentClient.cancel(
                payment.getPaymentKey(), refund.getReason().getCancelReason(), refund.getIdempotencyKey());
        paymentRefundService.record(refund.getId(), result);
    }
}
