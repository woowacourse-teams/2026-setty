package setty.platform.order.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import setty.common.DeliveryStatus;
import setty.common.OrderStatusNotified;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.platform.order.domain.Order;
import setty.platform.order.repository.OrderRepository;

@ExtendWith(MockitoExtension.class)
class SyncOrderDeliveryStatusServiceUnitTest {

    private static final Instant CHANGED_AT = Instant.parse("2026-08-31T01:00:00Z");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStatusNotifier orderStatusNotifier;

    @InjectMocks
    private SyncOrderDeliveryStatusService service;

    @ParameterizedTest
    @MethodSource("invalidValues")
    void 잘못된_배송_값은_주문을_조회하기_전에_거부한다(
            final Long deliveryId,
            final Long orderId,
            final Instant changedAt,
            final DeliveryStatus status
    ) {
        assertThatThrownBy(() -> service.sync(deliveryId, orderId, changedAt, status))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);

        verifyNoInteractions(orderRepository);
    }

    @Test
    void 배송_상태가_바뀌면_알림_이벤트를_한_번만_낸다() {
        final Order order = new Order(10L, 20L);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        service.sync(1L, 1L, CHANGED_AT, DeliveryStatus.ACCEPTED);
        service.sync(1L, 1L, CHANGED_AT, DeliveryStatus.ACCEPTED);

        verify(orderStatusNotifier).notify(order, OrderStatusNotified.Change.DELIVERY_ACCEPTED, CHANGED_AT);
    }

    @Test
    void 재배차_수락은_새_시각으로_다시_알린다() {
        final Order order = new Order(10L, 20L);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        final Instant reacceptedAt = CHANGED_AT.plusSeconds(120);

        service.sync(1L, 1L, CHANGED_AT, DeliveryStatus.ACCEPTED);
        service.reopen(1L, 1L, CHANGED_AT, CHANGED_AT.plusSeconds(60));
        service.sync(2L, 1L, reacceptedAt, DeliveryStatus.ACCEPTED);

        verify(orderStatusNotifier).notify(order, OrderStatusNotified.Change.DELIVERY_ACCEPTED, CHANGED_AT);
        verify(orderStatusNotifier).notify(order, OrderStatusNotified.Change.DELIVERY_ACCEPTED, reacceptedAt);
    }

    @Test
    void 픽업과_배송_완료도_각각_알린다() {
        final Order order = new Order(10L, 20L);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        service.sync(1L, 1L, CHANGED_AT, DeliveryStatus.ACCEPTED);
        service.sync(1L, 1L, CHANGED_AT.plusSeconds(1), DeliveryStatus.PICKED_UP);
        service.sync(1L, 1L, CHANGED_AT.plusSeconds(2), DeliveryStatus.DELIVERED);

        verify(orderStatusNotifier).notify(order, OrderStatusNotified.Change.DELIVERY_PICKED_UP, CHANGED_AT.plusSeconds(1));
        verify(orderStatusNotifier).notify(order, OrderStatusNotified.Change.DELIVERY_DELIVERED, CHANGED_AT.plusSeconds(2));
    }

    private static Stream<Arguments> invalidValues() {
        return Stream.of(
                Arguments.of(null, 1L, CHANGED_AT, DeliveryStatus.ACCEPTED),
                Arguments.of(0L, 1L, CHANGED_AT, DeliveryStatus.ACCEPTED),
                Arguments.of(-1L, 1L, CHANGED_AT, DeliveryStatus.ACCEPTED),
                Arguments.of(1L, null, CHANGED_AT, DeliveryStatus.ACCEPTED),
                Arguments.of(1L, 0L, CHANGED_AT, DeliveryStatus.ACCEPTED),
                Arguments.of(1L, -1L, CHANGED_AT, DeliveryStatus.ACCEPTED),
                Arguments.of(1L, 1L, null, DeliveryStatus.ACCEPTED),
                Arguments.of(1L, 1L, CHANGED_AT, null)
        );
    }
}
