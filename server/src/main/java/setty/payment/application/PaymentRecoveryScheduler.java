package setty.payment.application;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 결과를 모르는 승인 정리와 환불을 결제 전용 스레드 하나에서 차례로 돌린다.
 * 스케줄링 스레드는 기본 1개를 주문 만료 등 다른 배치와 함께 쓰므로, 토스 응답을 최대 60초 기다리는 일은 거기서 하지 않고 넘기기만 한다.
 * 앞 실행이 끝나지 않았으면 이번 주기는 건너뛴다.
 * 전용 스레드를 빈으로 등록하면 스프링이 기본 스케줄러·실행기를 만들지 않으므로 이 클래스 안에서만 만든다.
 */
@Component
public class PaymentRecoveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentRecoveryScheduler.class);

    private final UnknownPaymentResolver unknownPaymentResolver;
    private final PaymentRefundProcessor paymentRefundProcessor;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("payment-recovery").daemon(true).factory());
    private final AtomicBoolean running = new AtomicBoolean();

    public PaymentRecoveryScheduler(
            final UnknownPaymentResolver unknownPaymentResolver,
            final PaymentRefundProcessor paymentRefundProcessor
    ) {
        this.unknownPaymentResolver = unknownPaymentResolver;
        this.paymentRefundProcessor = paymentRefundProcessor;
    }

    @Scheduled(
            fixedDelayString = "${setty.payment.recovery.interval:PT5M}",
            initialDelayString = "${setty.payment.recovery.interval:PT5M}"
    )
    public void trigger() {
        if (!running.compareAndSet(false, true)) {
            log.info("이전 결제 복구 배치가 아직 실행 중이라 이번 주기를 건너뜁니다.");
            return;
        }
        worker.execute(() -> {
            try {
                runOnce();
            } finally {
                running.set(false);
            }
        });
    }

    // 정리 배치가 남긴 늦은 승인은 이벤트를 거쳐 환불 대상이 되므로, 보통 다음 주기의 환불에서 처리된다.
    void runOnce() {
        try {
            unknownPaymentResolver.resolveStale();
        } catch (final RuntimeException exception) {
            log.error("결과를 모르는 결제 정리 배치가 실패했습니다.", exception);
        }
        try {
            paymentRefundProcessor.processRequested();
        } catch (final RuntimeException exception) {
            log.error("환불 배치가 실패했습니다.", exception);
        }
    }

    @PreDestroy
    void shutdown() {
        worker.shutdownNow();
    }
}
