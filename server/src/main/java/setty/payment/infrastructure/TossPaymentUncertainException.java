package setty.payment.infrastructure;

import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

/**
 * 토스 승인 결과를 끝내 확인하지 못했다. 승인됐을 수 있으므로 시도 기록을 지우지 않는다.
 * 응답은 명확한 실패와 같은 {@code PAYMENT_CONFIRM_FAILED}다.
 */
public class TossPaymentUncertainException extends BusinessException {

    public TossPaymentUncertainException() {
        super(ErrorCode.PAYMENT_CONFIRM_FAILED);
    }
}
