package setty.payment.infrastructure;

import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

/**
 * 토스 승인 결과를 끝내 확인하지 못했다. 승인됐을 수 있으므로 시도 기록을 지우지 않는다.
 * 구매자 돈이 나갔을 수 있어 "승인 실패"가 아니라 다른 결제 키 차단과 같은 {@code PAYMENT_IN_PROGRESS}(결과 확인 중)로 답한다.
 */
public class TossPaymentUncertainException extends BusinessException {

    public TossPaymentUncertainException() {
        super(ErrorCode.PAYMENT_IN_PROGRESS);
    }
}
