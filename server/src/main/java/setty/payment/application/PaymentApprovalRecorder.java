package setty.payment.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.payment.domain.Payment;
import setty.payment.domain.PaymentRefundReason;
import setty.platform.PaymentAcceptance;
import setty.platform.PaymentOrderAcceptance;

/**
 * 토스 승인을 확인한 결제를 기록한다. 주문이 먼저 결과를 정해 커밋하고, 결제는 그 결과에 따라 기록한다(docs/policy/payment.md 승인·재시도).
 *
 * <p>두 커밋 사이에 중단되면 주문은 결과를 이미 정했고 결제는 UNKNOWN으로 남는다. 주문의 결과는 만료되지 않으므로
 * 정리 배치({@link UnknownPaymentResolver})가 토스에 다시 물어 같은 순서로 기록하면 같은 결과를 받는다.
 * 트랜잭션 밖에서 불러야 주문 결정과 결제 기록이 따로 커밋된다.
 */
@Component
@RequiredArgsConstructor
public class PaymentApprovalRecorder {

    private static final Logger log = LoggerFactory.getLogger(PaymentApprovalRecorder.class);

    private final PaymentOrderAcceptance paymentOrderAcceptance;
    private final PaymentRecorder paymentRecorder;
    private final Clock clock;

    public Payment record(
            final Long orderId,
            final String tossOrderId,
            final String paymentKey,
            final int amount,
            final LocalDateTime approvedAt
    ) {
        // 승인을 확인한 시각. 주문 잠금을 기다리기 전에 정해 둔다.
        final Instant confirmedAt = clock.instant();
        final PaymentAcceptance acceptance = decideOrder(orderId, confirmedAt);
        final PaymentRefundReason refundReason = acceptance.accepted()
                ? null
                : PaymentRefundReason.ofOrderStatus(acceptance.orderStatus());
        return paymentRecorder.recordCompleted(orderId, tossOrderId, paymentKey, amount, approvedAt, refundReason);
    }

    // 승인은 됐는데 주문 결정을 못 했다. 결제는 UNKNOWN으로 남아 정리 배치가 이어받으므로 "결과 확인 중"으로 답한다.
    private PaymentAcceptance decideOrder(final Long orderId, final Instant confirmedAt) {
        try {
            return paymentOrderAcceptance.accept(orderId, confirmedAt);
        } catch (final RuntimeException exception) {
            log.error("승인된 결제의 주문 결정에 실패했습니다. 결제는 결과 확인 중으로 남습니다. orderId={}", orderId, exception);
            throw new BusinessException(ErrorCode.PAYMENT_IN_PROGRESS);
        }
    }
}
