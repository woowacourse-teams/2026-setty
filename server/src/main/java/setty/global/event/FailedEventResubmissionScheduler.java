package setty.global.event;

import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 처리에 실패한 모듈 간 이벤트를 주기적으로 다시 발행한다. 리스너는 같은 이벤트를 여러 번 받아도 되도록 멱등하게 처리한다.
 * 최대 시도 횟수를 넘긴 건은 발행 기록(EVENT_PUBLICATION)에 FAILED로 남겨 원인을 확인한 뒤 처리한다.
 */
@Component
@EnableScheduling
@RequiredArgsConstructor
public class FailedEventResubmissionScheduler {

    // 최초 처리 1회를 포함한 시도 횟수
    private static final int MAX_COMPLETION_ATTEMPTS = 5;

    private final FailedEventPublications failedEventPublications;

    @Scheduled(
            fixedDelayString = "${setty.events.resubmission-interval:PT1M}",
            initialDelayString = "${setty.events.resubmission-interval:PT1M}"
    )
    public void resubmitFailedPublications() {
        failedEventPublications.resubmit(ResubmissionOptions.defaults()
                .withFilter(publication -> publication.getCompletionAttempts() < MAX_COMPLETION_ATTEMPTS));
    }
}
