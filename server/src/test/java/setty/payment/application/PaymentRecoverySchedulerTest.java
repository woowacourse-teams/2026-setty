package setty.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PaymentRecoverySchedulerTest {

    private final UnknownPaymentResolver resolver = mock(UnknownPaymentResolver.class);
    private final PaymentRefundProcessor refundProcessor = mock(PaymentRefundProcessor.class);
    private final PaymentRecoveryScheduler scheduler = new PaymentRecoveryScheduler(resolver, refundProcessor);

    @AfterEach
    void shutdown() {
        scheduler.shutdown();
    }

    @Test
    void 결제_전용_스레드에서_정리_뒤_환불을_돌리고_스케줄링_스레드는_기다리지_않는다() throws Exception {
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicReference<String> threadName = new AtomicReference<>();
        doAnswer(invocation -> {
            threadName.set(Thread.currentThread().getName());
            release.await(5, TimeUnit.SECONDS);
            return null;
        }).when(resolver).resolveStale();

        scheduler.trigger();

        verify(resolver, timeout(2_000)).resolveStale();
        assertThat(threadName.get()).isEqualTo("payment-recovery");
        release.countDown();
        verify(refundProcessor, timeout(2_000)).processRequested();
    }

    @Test
    void 앞_실행이_끝나지_않았으면_이번_주기는_건너뛴다() throws Exception {
        final CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> release.await(5, TimeUnit.SECONDS)).when(resolver).resolveStale();

        scheduler.trigger();
        verify(resolver, timeout(2_000)).resolveStale();
        scheduler.trigger();
        release.countDown();

        verify(refundProcessor, timeout(2_000)).processRequested();
        verify(resolver).resolveStale();
    }

    @Test
    void 정리_배치가_실패해도_환불_배치는_돈다() {
        doThrow(new IllegalStateException("조회 실패")).when(resolver).resolveStale();

        scheduler.runOnce();

        verify(refundProcessor).processRequested();
    }
}
