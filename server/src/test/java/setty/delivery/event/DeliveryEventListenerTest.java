package setty.delivery.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import setty.common.OrderCancellationRequested;
import setty.common.OrderConfirmed;
import setty.delivery.application.DeliveryLifecycleService;
import setty.delivery.application.RegisterDeliveryService;
import setty.delivery.domain.OrderId;
import setty.delivery.domain.delivery.DeliveryPoint;
import setty.delivery.domain.delivery.DeliveryRoute;
import setty.delivery.domain.delivery.EstimatedDeliveryFee;
import setty.delivery.domain.delivery.FurnitureInfo;

class DeliveryEventListenerTest {

    private final RegisterDeliveryService registerDeliveryService = mock(RegisterDeliveryService.class);
    private final DeliveryLifecycleService deliveryLifecycleService = mock(DeliveryLifecycleService.class);
    private final DeliveryEventListener listener =
            new DeliveryEventListener(registerDeliveryService, deliveryLifecycleService);

    @Test
    void translatesOrderConfirmedIntoDomainValuesAndDelegates() {
        listener.handle(new OrderConfirmed(
                101L,
                "가상 원목 의자",
                "CHAIR",
                "서울시 가상구 출발로 1",
                "서울시 가상구 도착로 2",
                10_000,
                "010-0000-0001",
                "010-0000-0002",
                10L,
                1L,
                150_000,
                "1111053000",
                "1111060000",
                "04524",
                "06236"
        ));

        verify(registerDeliveryService).register(
                eq(OrderId.from(101L)),
                eq(FurnitureInfo.of("가상 원목 의자", "CHAIR")),
                eq(DeliveryRoute.of(
                        DeliveryPoint.pickup("서울시 가상구 출발로 1", "010-0000-0001", "1111060000", "04524"),
                        DeliveryPoint.destination("서울시 가상구 도착로 2", "010-0000-0002", "1111053000", "06236")
                )),
                eq(EstimatedDeliveryFee.from(10_000)),
                any(Instant.class)
        );
    }

    @Test
    void 구역이_없는_주문도_배송_요청으로_번역한다() {
        listener.handle(new OrderConfirmed(101L, "가상 원목 의자", "CHAIR", "출발 주소", "도착 주소", 10_000,
                "010-0000-0001", "010-0000-0002", 10L, 1L, 150_000, null, null, null, null));

        verify(registerDeliveryService).register(
                eq(OrderId.from(101L)),
                any(),
                eq(DeliveryRoute.of(
                        DeliveryPoint.pickup("출발 주소", "010-0000-0001", null, null),
                        DeliveryPoint.destination("도착 주소", "010-0000-0002", null, null)
                )),
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
    void cancellationFailurePropagatesSoPublicationStaysIncomplete() {
        doThrow(new IllegalStateException("처리 실패"))
                .when(deliveryLifecycleService)
                .cancel(any(), any(), any());

        assertThatThrownBy(() -> listener.handle(new OrderCancellationRequested(101L, "cancel-request-1")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void invalidOrderConfirmedPropagatesSoPublicationStaysIncomplete() {
        assertThatThrownBy(() -> listener.handle(new OrderConfirmed(
                0L, "가상 원목 의자", "CHAIR", "서울시 가상구 출발로 1", "서울시 가상구 도착로 2",
                10_000, "010-0000-0001", "010-0000-0002", 10L, 1L, 150_000,
                null, null, null, null
        ))).isInstanceOf(RuntimeException.class);
    }

    @ParameterizedTest
    @ValueSource(classes = {OrderConfirmed.class, OrderCancellationRequested.class})
    void inboundEventsRunInNewTransactionAfterPublisherCommits(final Class<?> eventType) throws NoSuchMethodException {
        final Method handle = DeliveryEventListener.class.getMethod("handle", eventType);

        final TransactionalEventListener listenerAnnotation =
                AnnotatedElementUtils.findMergedAnnotation(handle, TransactionalEventListener.class);
        final Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(handle, Transactional.class);

        assertThat(handle.isAnnotationPresent(ApplicationModuleListener.class)).isTrue();
        assertThat(listenerAnnotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
        assertThat(listenerAnnotation.fallbackExecution()).isFalse();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
