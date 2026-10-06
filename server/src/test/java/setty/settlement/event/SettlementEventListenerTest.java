package setty.settlement.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import setty.common.DeliveryDelivered;
import setty.common.OrderCancelled;
import setty.common.OrderCompleted;
import setty.common.OrderConfirmed;
import setty.settlement.application.SettlementService;

class SettlementEventListenerTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-10-06T01:00:00Z");

    private final SettlementService settlementService = mock(SettlementService.class);
    private final SettlementEventListener listener = new SettlementEventListener(settlementService);

    @Test
    void 주문_확정으로_판매자_정산을_기록한다() {
        listener.handle(new OrderConfirmed(
                1L, "가상 원목 의자", "CHAIR", "서울시 가상구 출발로 1", "서울시 가상구 도착로 2",
                10_000, "010-0000-0001", "010-0000-0002", 100L, 10L, 150_000
        ));

        verify(settlementService).recordSeller(eq(1L), eq(10L), eq(100L), eq(150_000), any(Instant.class));
    }

    @Test
    void 배송_완료로_기사_정산을_기록한다() {
        listener.handle(new DeliveryDelivered(5L, 1L, OCCURRED_AT, 20L, 10_000));

        verify(settlementService).recordDriver(eq(1L), eq(20L), eq(10_000), any(Instant.class));
    }

    @Test
    void 판매_완료로_정산을_확정한다() {
        listener.handle(new OrderCompleted(1L, OCCURRED_AT));

        verify(settlementService).complete(1L, OCCURRED_AT);
    }

    @Test
    void 주문_취소로_정산을_취소한다() {
        listener.handle(new OrderCancelled(1L, 100L, "cancel-request-1", OCCURRED_AT));

        verify(settlementService).cancel(1L, OCCURRED_AT);
    }

    @ParameterizedTest
    @ValueSource(classes = {OrderConfirmed.class, DeliveryDelivered.class, OrderCompleted.class, OrderCancelled.class})
    void 발행한_쪽이_커밋된_뒤_새_트랜잭션에서_처리한다(final Class<?> eventType) throws NoSuchMethodException {
        final Method handle = SettlementEventListener.class.getMethod("handle", eventType);

        final TransactionalEventListener listenerAnnotation =
                AnnotatedElementUtils.findMergedAnnotation(handle, TransactionalEventListener.class);
        final Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(handle, Transactional.class);

        assertThat(handle.isAnnotationPresent(ApplicationModuleListener.class)).isTrue();
        assertThat(listenerAnnotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
