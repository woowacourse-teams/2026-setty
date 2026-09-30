package setty.delivery.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import setty.common.OrderCancellationRequested;
import setty.common.OrderRequested;
import setty.delivery.application.DeliveryLifecycleService;
import setty.delivery.application.RegisterDeliveryService;
import setty.delivery.domain.OrderId;
import setty.delivery.domain.delivery.FurnitureInfo;

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
                eq(OrderId.from(101L)),
                eq(FurnitureInfo.of("가상 원목 의자", "CHAIR")),
                any(),
                any(),
                any(Instant.class)
        );
    }

    @Test
    void translatesOrderCancellationRequestedIntoDomainValuesAndDelegates() {
        listener.handle(new OrderCancellationRequested(101L, "cancel-request-1"));

        verify(deliveryLifecycleService).cancel(
                eq(OrderId.from(101L)),
                eq("cancel-request-1"),
                any(Instant.class)
        );
    }

    @Test
    void cancellationFailureDoesNotEscapeListener() {
        doThrow(new IllegalStateException("처리 실패"))
                .when(deliveryLifecycleService)
                .cancel(any(), any(), any());

        assertThatCode(() -> listener.handle(new OrderCancellationRequested(101L, "cancel-request-1")))
                .doesNotThrowAnyException();
    }

    @Test
    void invalidOrderRequestedDoesNotEscapeListener() {
        assertThatCode(() -> listener.handle(new OrderRequested(
                0L, "가상 원목 의자", "CHAIR", "서울시 가상구 출발로 1", "서울시 가상구 도착로 2",
                10_000, "010-0000-0001", "010-0000-0002"
        ))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(classes = {OrderRequested.class, OrderCancellationRequested.class})
    void inboundEventsRunAfterPublisherCommits(final Class<?> eventType) throws NoSuchMethodException {
        final Method handle = DeliveryEventListener.class.getMethod("handle", eventType);

        final TransactionalEventListener annotation = handle.getAnnotation(TransactionalEventListener.class);

        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
        assertThat(annotation.fallbackExecution()).isTrue();
    }
}
