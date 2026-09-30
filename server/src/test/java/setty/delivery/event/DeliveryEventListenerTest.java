package setty.delivery.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import setty.common.OrderCancellationRequested;
import setty.common.OrderRequested;
import setty.delivery.application.DeliveryLifecycleService;
import setty.delivery.application.RegisterDeliveryService;
import setty.delivery.domain.FurnitureInfo;
import setty.delivery.domain.OrderId;

class DeliveryEventListenerTest {

    private final RegisterDeliveryService registerDeliveryService = mock(RegisterDeliveryService.class);
    private final DeliveryLifecycleService deliveryLifecycleService = mock(DeliveryLifecycleService.class);
    private final DeliveryEventListener listener =
            new DeliveryEventListener(registerDeliveryService, deliveryLifecycleService);

    @Test
    void translatesOrderRequestedIntoDomainValuesAndDelegates() {
        listener.handle(new OrderRequested(
                101L,
                "가상 원목 의자",
                "CHAIR",
                "서울시 가상구 출발로 1",
                "서울시 가상구 도착로 2",
                10_000,
                "010-0000-0001",
                "010-0000-0002"
        ));

        verify(registerDeliveryService).register(
                eq(new OrderId(101L)),
                eq(new FurnitureInfo("가상 원목 의자", "CHAIR")),
                any(),
                any(),
                any(Instant.class)
        );
    }

    @Test
    void translatesOrderCancellationRequestedIntoDomainValuesAndDelegates() {
        listener.handle(new OrderCancellationRequested(101L, "cancel-request-1"));

        verify(deliveryLifecycleService).cancel(
                eq(new OrderId(101L)),
                eq("cancel-request-1"),
                any(Instant.class)
        );
    }
}
