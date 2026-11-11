package setty.aisupport;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static setty.aisupport.SupportDtos.*;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SupportSlotWaitTest {
    private final SupportStore store = mock(SupportStore.class);
    private final SupportRuntime runtime = mock(SupportRuntime.class);
    private final CountDownLatch entered = new CountDownLatch(1);
    private final AtomicReference<Thread> worker = new AtomicReference<>();
    private final SendRequest request = new SendRequest("waiting", new Input(InputKind.TEXT, "주문 문의", null, null));
    private final Turn turn = new Turn(1, request.clientRequestId(), request.input(), TurnStatus.SUCCEEDED,
            Instant.EPOCH, Instant.EPOCH, "답변", List.of(), null);

    private SupportService service(long waitMs) throws InterruptedException {
        var service = SupportServiceFixture.service(store, runtime,
                new SupportConfiguration.Settings(true, null, null, 1, "bedrock", waitMs));
        when(store.existing(1, 1, request)).thenAnswer(invocation -> {
            worker.set(Thread.currentThread());
            entered.countDown();
            return null;
        });
        when(store.selected(1, 1)).thenReturn(null);
        when(store.accept(1, 1, request)).thenReturn(new SupportStore.Acceptance(turn, true, null));
        when(runtime.run(eq(1L), eq(turn), anyList(), isNull(), isNull(), isNull()))
                .thenReturn(new Result("답변", List.of(), 0, 0, List.of()));
        when(store.complete(eq(1L), eq(1L), eq(1L), any(), isNull(), anyString(), anyLong())).thenReturn(turn);
        slots(service).acquire(); // Simulate an occupied slot without holding a model thread or sleeping.
        return service;
    }

    @Test void releasedSlotProcessesWaitingRequestWithinDeadline() throws Exception {
        var service = service(500);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var future = pool.submit(() -> service.send(1, 1, request));
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> future.get(20, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            slots(service).release();
            assertThat(future.get(1, TimeUnit.SECONDS)).isEqualTo(turn);
            verify(runtime).run(eq(1L), eq(turn), anyList(), isNull(), isNull(), isNull());
            assertThat(slots(service).availablePermits()).isEqualTo(1);
        } finally {
            if (slots(service).availablePermits() == 0) slots(service).release();
        }
    }

    @Test void occupiedSlotTimesOutWithoutAcceptingTurn() throws Exception {
        var service = service(25);
        try {
            assertBusy(() -> service.send(1, 1, request));
            assertThat(slots(service).availablePermits()).isZero();
            verify(store, never()).accept(anyLong(), anyLong(), any());
            verifyNoInteractions(runtime);
        } finally {
            slots(service).release();
        }
    }

    @Test void zeroWaitRejectsImmediately() throws Exception {
        var service = service(0);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var future = pool.submit(() -> assertBusy(() -> service.send(1, 1, request)));
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            future.get(100, TimeUnit.MILLISECONDS); // A mistakenly used default 2000ms cannot pass.
            assertThat(slots(service).availablePermits()).isZero();
            verify(store, never()).accept(anyLong(), anyLong(), any());
            verifyNoInteractions(runtime);
        } finally {
            slots(service).release();
        }
    }

    @Test void interruptionRestoresFlagAndReturnsBusyWithoutReleasingUnownedPermit() throws Exception {
        var service = service(500);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var future = pool.submit(() -> {
                assertBusy(() -> service.send(1, 1, request));
                return Thread.currentThread().isInterrupted();
            });
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> future.get(20, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            worker.get().interrupt();
            assertThat(future.get(1, TimeUnit.SECONDS)).isTrue();
            assertThat(slots(service).availablePermits()).isZero();
            verify(store, never()).accept(anyLong(), anyLong(), any());
            verifyNoInteractions(runtime);
        } finally {
            slots(service).release();
        }
    }

    private static Semaphore slots(SupportService service) {
        return (Semaphore) ReflectionTestUtils.getField(service, "slots");
    }
    private static void assertBusy(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(SupportException.class, error -> {
            assertThat(error.status).isEqualTo(429);
            assertThat(error.code).isEqualTo("AI_BUSY");
            assertThat(error.getMessage()).isEqualTo("상담 요청이 많습니다. 잠시 후 다시 시도해 주세요.");
        });
    }
}
