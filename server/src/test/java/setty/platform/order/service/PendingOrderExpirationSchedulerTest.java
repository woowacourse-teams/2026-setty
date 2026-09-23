package setty.platform.order.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import setty.common.DeliveryStatus;
import setty.platform.order.repository.OrderRepository;

@ExtendWith(MockitoExtension.class)
class PendingOrderExpirationSchedulerTest {

    private static final Instant REFERENCE_TIME = Instant.parse("2026-09-02T05:11:00Z");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PendingOrderExpirationService expirationService;

    @Mock
    private Clock clock;

    @InjectMocks
    private PendingOrderExpirationScheduler scheduler;

    @Test
    void 만료된_PENDING_주문_후보를_각각_처리한다() {
        when(clock.instant()).thenReturn(REFERENCE_TIME);
        when(orderRepository.findExpiredOrderIds(DeliveryStatus.PENDING, REFERENCE_TIME))
                .thenReturn(List.of(101L, 102L));

        scheduler.expirePendingOrders();

        verify(expirationService).expire(101L, REFERENCE_TIME);
        verify(expirationService).expire(102L, REFERENCE_TIME);
    }

    @Test
    void 한_주문_처리가_실패해도_다음_주문을_계속_처리한다() {
        when(clock.instant()).thenReturn(REFERENCE_TIME);
        when(orderRepository.findExpiredOrderIds(DeliveryStatus.PENDING, REFERENCE_TIME))
                .thenReturn(List.of(101L, 102L));
        when(expirationService.expire(101L, REFERENCE_TIME)).thenThrow(new RuntimeException("가상 오류"));

        scheduler.expirePendingOrders();

        verify(expirationService).expire(102L, REFERENCE_TIME);
    }
}
