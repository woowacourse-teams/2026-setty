package setty.payment.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import setty.payment.domain.Payment;
import setty.payment.domain.PaymentStatus;
import setty.payment.infrastructure.TossConfirmResult;
import setty.payment.infrastructure.TossPaymentClient;
import setty.payment.infrastructure.TossPaymentLookup;
import setty.payment.repository.PaymentRepository;

/**
 * 승인 결과를 끝내 확인하지 못해 UNKNOWN으로 남은 결제를 토스에 다시 묻는다.
 * 승인됐으면 주문의 결과를 먼저 정하고 기록한다. 주문이 받아들였으면 그대로, 이미 끝났으면 환불 대상도 함께 기록한다.
 * 승인되지 않은 결제면 시도를 지운다. 토스 호출은 트랜잭션 밖에서 한다.
 */
@Component
@RequiredArgsConstructor
public class UnknownPaymentResolver {

    private static final Logger log = LoggerFactory.getLogger(UnknownPaymentResolver.class);

    private final PaymentRepository paymentRepository;
    private final PaymentRecorder paymentRecorder;
    private final PaymentApprovalRecorder paymentApprovalRecorder;
    private final TossPaymentClient tossPaymentClient;
    private final PaymentRecoveryProperties properties;
    private final Clock clock;

    public void resolveStale() {
        final Instant attemptedBefore = clock.instant().minus(properties.staleAfter());
        final List<Payment> stale = paymentRepository.findByStatusAndAttemptedAtLessThanEqualOrderByAttemptedAtAscIdAsc(
                PaymentStatus.UNKNOWN, attemptedBefore, Limit.of(properties.batchSize()));
        for (final Payment payment : stale) {
            try {
                resolve(payment);
            } catch (final RuntimeException exception) {
                log.warn("결과를 모르는 결제 정리에 실패했습니다. orderId={}", payment.getOrderId(), exception);
            }
        }
    }

    private void resolve(final Payment payment) {
        switch (tossPaymentClient.find(payment.getPaymentKey())) {
            case TossPaymentLookup.Found(final TossConfirmResult found)
                    when found.isDoneFor(payment.getTossOrderId(), payment.getAmount()) -> {
                paymentApprovalRecorder.record(payment.getOrderId(), payment.getTossOrderId(),
                        payment.getPaymentKey(), payment.getAmount(), found.approvedAtAsLocalDateTime());
                log.info("결과를 모르던 결제가 토스에서 승인되어 기록했습니다. orderId={}", payment.getOrderId());
            }
            case TossPaymentLookup.Found(final TossConfirmResult found) when found.isNotApproved() ->
                    discard(payment, found.status());
            case TossPaymentLookup.NotFound notFound -> discard(payment, "NOT_FOUND_PAYMENT");
            case TossPaymentLookup.Found(final TossConfirmResult found) when "DONE".equals(found.status()) ->
                    log.error("토스에서 승인된 결제가 주문과 맞지 않습니다. 수동 확인이 필요합니다. orderId={}, totalAmount={}",
                            payment.getOrderId(), found.totalAmount());
            case TossPaymentLookup.Found(final TossConfirmResult found) ->
                    log.warn("토스 결제가 아직 끝나지 않아 다음 배치에서 다시 확인합니다. orderId={}, status={}",
                            payment.getOrderId(), found.status());
            case TossPaymentLookup.Unavailable unavailable ->
                    log.warn("토스 결제를 조회하지 못해 다음 배치에서 다시 확인합니다. orderId={}", payment.getOrderId());
        }
    }

    private void discard(final Payment payment, final String tossStatus) {
        paymentRecorder.discardAttempt(payment.getOrderId(), payment.getPaymentKey());
        log.info("승인되지 않은 결제 시도를 지웠습니다. orderId={}, tossStatus={}", payment.getOrderId(), tossStatus);
    }
}
