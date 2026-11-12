package setty.payment.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class PaymentConfirmLimiterTest {

    private static final long SECOND = 1_000_000_000L;

    private final AtomicLong now = new AtomicLong();

    @Test
    void 한_번에_capacity만큼_받고_그_다음은_거절한다() {
        final PaymentConfirmLimiter limiter = new PaymentConfirmLimiter(3, 1, now::get);

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
    }

    @Test
    void 시간이_지나면_초당_보충량만큼_다시_받는다() {
        final PaymentConfirmLimiter limiter = new PaymentConfirmLimiter(2, 2, now::get);
        limiter.tryAcquire();
        limiter.tryAcquire();

        now.addAndGet(SECOND / 2);
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
    }

    @Test
    void 오래_쉬어도_capacity보다_많이_쌓이지_않는다() {
        final PaymentConfirmLimiter limiter = new PaymentConfirmLimiter(2, 1, now::get);

        now.addAndGet(60 * SECOND);

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
    }
}
