package setty.platform.order.controller;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import setty.common.DeliveryCancelled;
import setty.common.DeliveryCancellationRejected;
import setty.common.PaymentCompleted;
import setty.common.PaymentFailed;
import setty.platform.order.service.OrderService;
import setty.platform.order.service.SyncOrderDeliveryStatusService;

@ExtendWith(MockitoExtension.class)
class OrderEventListenerTest {

    @Mock
    private OrderService orderService;

    @Mock
    private SyncOrderDeliveryStatusService syncOrderDeliveryStatusService;

    @InjectMocks
    private OrderEventListener listener;

    @Test
    void 결제완료_이벤트를_배송요청_이벤트_발행으로_위임한다() {
        listener.onPaymentCompleted(new PaymentCompleted(101L));

        verify(orderService).publishOrderConfirmed(101L);
    }

    @Test
    void 결제실패_이벤트를_결제대기_주문_취소로_위임한다() {
        listener.onPaymentFailed(new PaymentFailed(101L));

        verify(orderService).cancelPending(101L);
    }

    @Test
    void 배송_취소_성공_응답을_주문_취소_확정으로_위임한다() {
        final DeliveryCancelled event = new DeliveryCancelled(null, 101L, "cancel-1", java.time.Instant.now());

        listener.onDeliveryCancelled(event);

        verify(orderService).confirmCancellation(event);
    }

    @Test
    void 배송_취소_거절_응답을_주문_취소_거절로_위임한다() {
        final DeliveryCancellationRejected event =
                new DeliveryCancellationRejected(201L, 101L, "cancel-1", java.time.Instant.now());

        listener.onDeliveryCancellationRejected(event);

        verify(orderService).rejectCancellation(event);
    }
}
