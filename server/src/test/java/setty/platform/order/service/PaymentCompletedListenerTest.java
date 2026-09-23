package setty.platform.order.service;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import setty.common.PaymentCompleted;

@ExtendWith(MockitoExtension.class)
class PaymentCompletedListenerTest {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private PaymentCompletedListener listener;

    @Test
    void 결제완료_이벤트를_배송요청_이벤트_발행으로_위임한다() {
        listener.handle(new PaymentCompleted(101L));

        verify(orderService).publishOrderRequested(101L);
    }
}
