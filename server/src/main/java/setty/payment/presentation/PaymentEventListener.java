package setty.payment.presentation;

import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import setty.common.PaymentRefundRequested;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.payment.application.PaymentRefundService;
import setty.payment.domain.PaymentRefundReason;

/**
 * 다른 모듈에서 결제로 들어오는 이벤트의 진입 경계.
 * 발행한 쪽이 커밋된 뒤 결제의 새 트랜잭션에서 처리하고, 실패하면 발행 기록이 남아 재발행되므로 처리는 멱등하다.
 */
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final PaymentRefundService paymentRefundService;

    @ApplicationModuleListener
    public void onPaymentRefundRequested(final PaymentRefundRequested event) {
        if (event == null || event.orderId() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        paymentRefundService.request(event.orderId(), PaymentRefundReason.ofOrderStatus(event.orderStatus()));
    }
}
