package setty.delivery.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import setty.delivery.domain.DeliveryId;
import setty.delivery.domain.DriverId;
import setty.delivery.domain.OrderId;
import setty.delivery.domain.delivery.Delivery;
import setty.delivery.domain.delivery.DeliveryPoint;
import setty.delivery.domain.delivery.DeliveryRoute;
import setty.delivery.domain.delivery.EstimatedDeliveryFee;
import setty.delivery.domain.delivery.FurnitureInfo;
import setty.delivery.persistence.DeliveryOrderDecisionRepository;
import setty.delivery.persistence.DeliveryRepository;

class DeliveryRequestsChangedPublisherTest {

    private static final Instant REQUESTED_AT = Instant.parse("2026-08-31T01:00:00Z");

    @Test
    void registrationPublishesRequestListChangedEvent() {
        final DeliveryRepository deliveryRepository = mock(DeliveryRepository.class);
        final DeliveryOrderDecisionRepository decisionRepository = mock(DeliveryOrderDecisionRepository.class);
        final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        when(decisionRepository.decideRequested(any(OrderId.class), any(Instant.class))).thenReturn(true);
        final RegisterDeliveryService service = new RegisterDeliveryService(
                deliveryRepository,
                decisionRepository,
                eventPublisher
        );

        service.register(
                OrderId.from(1L),
                FurnitureInfo.of("가상 원목 의자", "CHAIR"),
                DeliveryRoute.of(
                        DeliveryPoint.pickup("서울시 가상구 출발로 1", "010-0000-0001"),
                        DeliveryPoint.destination("서울시 가상구 도착로 2", "010-0000-0002")
                ),
                EstimatedDeliveryFee.from(10_000),
                REQUESTED_AT
        );

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
        when(delivery.getOrderId()).thenReturn(OrderId.from(2L));
        when(deliveryRepository.findByIdForUpdate(anyLong())).thenReturn(Optional.of(delivery));
        final DeliveryLifecycleService service = new DeliveryLifecycleService(
                deliveryRepository,
                mock(DeliveryOrderDecisionRepository.class),
                eventPublisher
        );

        service.accept(new DeliveryId(1L), new DriverId(3L), REQUESTED_AT);

        verify(eventPublisher).publishEvent(isA(DeliveryRequestsChanged.class));
    }
}
