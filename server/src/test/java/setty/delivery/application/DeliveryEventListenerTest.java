package setty.delivery.application;

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
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import setty.common.OrderRequested;
import setty.delivery.domain.FurnitureInfo;
import setty.delivery.domain.OrderId;

class DeliveryEventListenerTest {

    private static OrderRequested orderRequested() {
        return new OrderRequested(
                101L,
                "가상 원목 의자",
                "CHAIR",
                "서울시 가상구 출발로 1",
                "서울시 가상구 도착로 2",
                10_000,
                "010-0000-0001",
                "010-0000-0002"
        );
    }

    @Test
    void translatesOrderRequestedIntoDomainValuesAndDelegates() {
        final RegisterDeliveryService registerDeliveryService = mock(RegisterDeliveryService.class);
        final DeliveryRequestNotifier notifier = mock(DeliveryRequestNotifier.class);
        final DeliveryEventListener listener = new DeliveryEventListener(registerDeliveryService, notifier);

        listener.handle(orderRequested());

        verify(registerDeliveryService).register(
                eq(new OrderId(101L)),
                eq(new FurnitureInfo("가상 원목 의자", "CHAIR")),
                any(),
                any(),
                any(Instant.class)
        );
    }

    @Test
    void notifiesRequestSubscribersAfterCommit() {
        final DeliveryRequestNotifier notifier = mock(DeliveryRequestNotifier.class);
        final DeliveryEventListener listener = new DeliveryEventListener(mock(RegisterDeliveryService.class), notifier);

        listener.handle(new DeliveryRequestsChanged());

        verify(notifier).notifyRequestsChanged();
    }

    @Test
    void notificationFailureDoesNotEscapeListener() {
        final DeliveryRequestNotifier notifier = mock(DeliveryRequestNotifier.class);
        doThrow(new IllegalStateException("연결 종료"))
                .when(notifier)
                .notifyRequestsChanged();
        final DeliveryEventListener listener = new DeliveryEventListener(mock(RegisterDeliveryService.class), notifier);

        assertThatCode(() -> listener.handle(new DeliveryRequestsChanged()))
                .doesNotThrowAnyException();
    }

    @Test
    void requestNotificationRunsAfterCommit() throws NoSuchMethodException {
        final Method handle = DeliveryEventListener.class
                .getMethod("handle", DeliveryRequestsChanged.class);

        final TransactionalEventListener annotation = handle.getAnnotation(TransactionalEventListener.class);

        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }
}
