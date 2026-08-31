package setty.delivery.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.InOrder;
import setty.common.OrderRequested;
import setty.delivery.domain.Delivery;
import setty.delivery.domain.DeliveryId;
import setty.delivery.domain.DriverId;
import setty.delivery.domain.OrderId;

class DeliveryRequestsChangedPublisherTest {

    private static final Instant REQUESTED_AT = Instant.parse("2026-08-31T01:00:00Z");

    @Test
    void registrationPublishesRequestListChangedEvent() {
        final DeliveryRepository deliveryRepository = mock(DeliveryRepository.class);
        final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        when(deliveryRepository.existsByOrderId(any(OrderId.class))).thenReturn(false);
        final RegisterDeliveryService service = new RegisterDeliveryService(deliveryRepository, eventPublisher);

        service.register(orderRequested(), REQUESTED_AT);

        final InOrder inOrder = inOrder(deliveryRepository, eventPublisher);
        inOrder.verify(deliveryRepository).save(any(Delivery.class));
        inOrder.verify(eventPublisher).publishEvent(isA(DeliveryRequestsChanged.class));
    }

    @Test
    void acceptancePublishesRequestListChangedEvent() {
        final DeliveryRepository deliveryRepository = mock(DeliveryRepository.class);
        final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        final Delivery delivery = mock(Delivery.class);
        when(delivery.getId()).thenReturn(new DeliveryId(1L));
        when(delivery.getOrderId()).thenReturn(new OrderId(2L));
        when(deliveryRepository.findById(any(DeliveryId.class))).thenReturn(Optional.of(delivery));
        final DeliveryLifecycleService service = new DeliveryLifecycleService(deliveryRepository, eventPublisher);

        service.accept(new DeliveryId(1L), new DriverId(3L), REQUESTED_AT);

        verify(eventPublisher).publishEvent(isA(DeliveryRequestsChanged.class));
    }

    private static OrderRequested orderRequested() {
        return new OrderRequested(
                1L,
                "가상 원목 의자",
                "CHAIR",
                "서울시 가상구 출발로 1",
                "서울시 가상구 도착로 2",
                10_000,
                "010-0000-0001",
                "010-0000-0002"
        );
    }
}
