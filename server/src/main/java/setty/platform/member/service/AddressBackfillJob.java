package setty.platform.member.service;

import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "setty.address.backfill.enabled", havingValue = "true")
public class AddressBackfillJob {

    private static final Logger log = LoggerFactory.getLogger(AddressBackfillJob.class);

    private final AddressBackfillService backfillService;
    private final int batchSize;
    private final Duration pause;

    public AddressBackfillJob(
            final AddressBackfillService backfillService,
            @Value("${setty.address.backfill.batch-size}") final int batchSize,
            @Value("${setty.address.backfill.pause}") final Duration pause
    ) {
        this.backfillService = backfillService;
        this.batchSize = batchSize;
        this.pause = pause;
    }

    // 끝날 때까지 오래 걸리므로 시작을 막지 않고, 이벤트 리스너 풀도 쓰지 않도록 전용 스레드에서 돈다.
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        Thread.ofPlatform().name("address-backfill").daemon().start(this::run);
    }

    public int run() {
        final long startedAt = System.nanoTime();
        long afterId = 0;
        int processed = 0;
        while (true) {
            List<Long> memberIds;
            try {
                memberIds = backfillService.backfillAfter(afterId, batchSize);
            } catch (final RuntimeException exception) {
                // 묶음이 롤백된 뒤 같은 범위를 한 건씩 다른 트랜잭션으로 다시 처리해 한 건의 오류를 격리한다.
                memberIds = backfillService.findUnresolvedIdsAfter(afterId, batchSize);
                log.warn("주소 채우기 묶음 실패, 단건으로 재시도합니다. memberIds={}", memberIds, exception);
                backfillIndividually(memberIds);
            }
            if (memberIds.isEmpty()) {
                break;
            }
            afterId = memberIds.getLast();
            processed += memberIds.size();
            if (!sleep()) {
                break;
            }
        }
        final long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        log.info("주소 채우기 완료. processed={}, remainingUnresolved={}, elapsedMillis={}, batchSize={}, pause={}",
                processed, backfillService.countUnresolved(), elapsedMillis, batchSize, pause);
        return processed;
    }

    private void backfillIndividually(final List<Long> memberIds) {
        for (final Long memberId : memberIds) {
            try {
                backfillService.backfill(memberId);
            } catch (final RuntimeException exception) {
                log.error("회원 주소 채우기에 실패했습니다. memberId={}", memberId, exception);
            }
        }
    }

    private boolean sleep() {
        if (pause.isZero()) {
            return true;
        }
        try {
            Thread.sleep(pause);
            return true;
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
