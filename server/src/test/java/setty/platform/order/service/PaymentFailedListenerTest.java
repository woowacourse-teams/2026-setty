package setty.platform.order.service;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import setty.common.PaymentFailed;

@ExtendWith(MockitoExtension.class)
class PaymentFailedListenerTest {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private PaymentFailedListener listener;

    @Test
    void 결제실패_이벤트를_결제대기_주문_취소로_위임한다() {
        listener.handle(new PaymentFailed(101L));

        verify(orderService).cancelPending(101L);
    }
}
