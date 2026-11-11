package setty.platform.order.controller;

import static org.mockito.Mockito.verify;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import setty.common.DeliveryCancelled;
import setty.common.DeliveryCancellationRejected;
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
    void 배송_취소_성공_응답을_주문_취소_확정으로_위임한다() {
        final Instant decidedAt = Instant.parse("2026-09-30T01:00:00Z");
        final DeliveryCancelled event = new DeliveryCancelled(null, 101L, "cancel-1", decidedAt);

        listener.onDeliveryCancelled(event);

        verify(orderService).confirmCancellation(101L, "cancel-1", decidedAt);
    }

    @Test
    void 배송_취소_거절_응답을_주문_취소_거절로_위임한다() {
        final Instant decidedAt = Instant.parse("2026-09-30T01:00:00Z");
        final DeliveryCancellationRejected event =
                new DeliveryCancellationRejected(201L, 101L, "cancel-1", decidedAt);

        listener.onDeliveryCancellationRejected(event);

        verify(orderService).rejectCancellation(101L, "cancel-1", decidedAt,"DRIVER_ACCEPTED_OR_LATER");
    }
}
