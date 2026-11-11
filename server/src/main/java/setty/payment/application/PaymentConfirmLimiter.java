package setty.payment.application;

import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 토스 승인 호출 수를 토큰 버킷으로 제한한다.
 * 승인은 요청 스레드에서 최대 토스 읽기 제한(60초)까지 기다리므로, 몰린 요청이 서버 스레드를 모두 붙잡지 않게
 * 토스를 부르기 전에 토큰을 하나 쓴다. 토큰이 없으면 토스를 부르지 않고 잠시 뒤 다시 하라고 답한다.
 */
@Component
public class PaymentConfirmLimiter {

    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    private final long capacity;
    private final double refillPerSecond;
    private final LongSupplier nanoClock;

    private double availableTokens;
    private long lastRefillNanos;

    @Autowired
    public PaymentConfirmLimiter(final PaymentConfirmLimitProperties properties) {
        this(properties.capacity(), properties.refillPerSecond(), System::nanoTime);
    }

    PaymentConfirmLimiter(final long capacity, final double refillPerSecond, final LongSupplier nanoClock) {
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
        this.nanoClock = nanoClock;
        this.availableTokens = capacity;
        this.lastRefillNanos = nanoClock.getAsLong();
    }

    /** 토큰이 있으면 하나 쓰고 true, 없으면 false. */
    public synchronized boolean tryAcquire() {
        refill();
        if (availableTokens < 1) {
            return false;
        }
        availableTokens -= 1;
        return true;
    }

    private void refill() {
        final long now = nanoClock.getAsLong();
        final double elapsedSeconds = (now - lastRefillNanos) / NANOS_PER_SECOND;
        availableTokens = Math.min(capacity, availableTokens + elapsedSeconds * refillPerSecond);
        lastRefillNanos = now;
    }
}
