package setty.platform.order.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import setty.platform.order.domain.OrderStatus;
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
    void 만료된_PENDING_주문_후보를_묶어서_처리한다() {
        when(clock.instant()).thenReturn(REFERENCE_TIME);
        when(orderRepository.findExpiredOrderIds(OrderStatus.PENDING, REFERENCE_TIME))
                .thenReturn(List.of(101L, 102L));

        scheduler.expirePendingOrders();

        verify(expirationService).expireBatch(List.of(101L, 102L), REFERENCE_TIME);
    }

    @Test
    void 묶음_실패시_단건으로_재시도하고_한_건이_실패해도_계속_처리한다() {
        when(clock.instant()).thenReturn(REFERENCE_TIME);
        when(orderRepository.findExpiredOrderIds(OrderStatus.PENDING, REFERENCE_TIME))
                .thenReturn(List.of(101L, 102L));
        when(expirationService.expireBatch(List.of(101L, 102L), REFERENCE_TIME))
                .thenThrow(new RuntimeException("묶음 처리 오류"));
        when(expirationService.expire(101L, REFERENCE_TIME)).thenThrow(new RuntimeException("가상 오류"));

        scheduler.expirePendingOrders();

        verify(expirationService).expire(102L, REFERENCE_TIME);
    }

    @Test
    void 한_트랜잭션에서_처리할_후보를_200건으로_제한한다() {
        final List<Long> ids = LongStream.rangeClosed(1, 401).boxed().toList();
        when(clock.instant()).thenReturn(REFERENCE_TIME);
        when(orderRepository.findExpiredOrderIds(OrderStatus.PENDING, REFERENCE_TIME)).thenReturn(ids);

        scheduler.expirePendingOrders();

        verify(expirationService).expireBatch(ids.subList(0, 200), REFERENCE_TIME);
        verify(expirationService).expireBatch(ids.subList(200, 400), REFERENCE_TIME);
        verify(expirationService).expireBatch(ids.subList(400, 401), REFERENCE_TIME);
    }

    @Test
    void 만료_후보가_없으면_처리하지_않는다() {
        when(clock.instant()).thenReturn(REFERENCE_TIME);
        when(orderRepository.findExpiredOrderIds(OrderStatus.PENDING, REFERENCE_TIME)).thenReturn(List.of());

        scheduler.expirePendingOrders();

        verifyNoInteractions(expirationService);
    }
}
