package setty.payment.application;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import setty.common.PaymentCompleted;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.payment.domain.Payment;
import setty.payment.repository.PaymentRepository;

/**
 * 토스 승인이 확정된 뒤 결제 저장과 이벤트 발행을 하나의 트랜잭션으로 묶는다.
 * 외부 결제 호출(토스)은 이 트랜잭션 밖에서 이미 끝난 상태로 들어온다.
 * 실패 복귀는 기록 없이 이벤트만 발행하므로 여기를 거치지 않는다({@code PaymentService.fail}).
 *
 * <p>주문은 결제 이전에 PENDING으로 존재하므로 여기서는 주문을 만들지 않고, 결과만 이벤트로 알린다.
 * 주문 상태 전이·배차 발행·선점 해제는 이벤트를 수신하는 플랫폼(주문) 팀이 담당한다.
 */
@Component
@RequiredArgsConstructor
public class PaymentRecorder {

    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 승인 성공 저장. 주문당 1행을 유지한다 — 기존 행(레거시 ABORTED 포함)이 있으면 DONE으로 전이하고, 없으면 새로 저장한다.
     * 이미 DONE이면 중복 승인이므로 {@code ALREADY_PAID}로 막는다(멱등 처리는 호출부에서 선반영).
     */
    @Transactional
    public Payment recordCompleted(
            final Long orderId,
            final String tossOrderId,
            final String paymentKey,
            final int amount,
            final java.time.LocalDateTime approvedAt
    ) {
        final Payment payment = paymentRepository.findByOrderId(orderId)
                .map(existing -> {
                    existing.markDone(tossOrderId, paymentKey, approvedAt);
                    return existing;
                })
                .orElseGet(() -> save(Payment.done(orderId, tossOrderId, paymentKey, amount, approvedAt)));

        eventPublisher.publishEvent(new PaymentCompleted(orderId));
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
