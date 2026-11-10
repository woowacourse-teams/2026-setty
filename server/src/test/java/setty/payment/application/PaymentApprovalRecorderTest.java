package setty.payment.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.payment.domain.PaymentRefundReason;
import setty.platform.PaymentAcceptance;
import setty.platform.PaymentOrderAcceptance;

@ExtendWith(MockitoExtension.class)
class PaymentApprovalRecorderTest {

    private static final Long ORDER_ID = 7L;
    private static final String TOSS_ORDER_ID = "7_token";
    private static final String PAYMENT_KEY = "pk";
    private static final int AMOUNT = 160_000;
    private static final LocalDateTime APPROVED_AT = LocalDateTime.of(2026, 11, 10, 12, 0);

    @Mock
    private PaymentOrderAcceptance paymentOrderAcceptance;

    @Mock
    private PaymentRecorder paymentRecorder;

    @InjectMocks
    private PaymentApprovalRecorder paymentApprovalRecorder;

    @Test
    void 주문이_먼저_결과를_정한_뒤_결제를_기록한다() {
        when(paymentOrderAcceptance.accept(ORDER_ID)).thenReturn(PaymentAcceptance.ofAccepted());

        paymentApprovalRecorder.record(ORDER_ID, TOSS_ORDER_ID, PAYMENT_KEY, AMOUNT, APPROVED_AT);

        final InOrder order = inOrder(paymentOrderAcceptance, paymentRecorder);
        order.verify(paymentOrderAcceptance).accept(ORDER_ID);
        order.verify(paymentRecorder).recordCompleted(ORDER_ID, TOSS_ORDER_ID, PAYMENT_KEY, AMOUNT, APPROVED_AT, null);
    }

    @Test
    void 주문이_받아들이지_않으면_끝난_상태에_맞는_사유로_환불_대상도_기록한다() {
        when(paymentOrderAcceptance.accept(ORDER_ID)).thenReturn(PaymentAcceptance.ofRejected("EXPIRED"));

        paymentApprovalRecorder.record(ORDER_ID, TOSS_ORDER_ID, PAYMENT_KEY, AMOUNT, APPROVED_AT);

        inOrder(paymentRecorder).verify(paymentRecorder).recordCompleted(
                ORDER_ID, TOSS_ORDER_ID, PAYMENT_KEY, AMOUNT, APPROVED_AT, PaymentRefundReason.ORDER_EXPIRED);
    }

    @Test
    void 주문_결정에_실패하면_결제를_기록하지_않고_결과_확인_중으로_답한다() {
        when(paymentOrderAcceptance.accept(any())).thenThrow(new IllegalStateException("DB 연결 끊김"));

        assertThatThrownBy(() -> paymentApprovalRecorder.record(ORDER_ID, TOSS_ORDER_ID, PAYMENT_KEY, AMOUNT, APPROVED_AT))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_IN_PROGRESS);
        verifyNoInteractions(paymentRecorder);
    }
}
