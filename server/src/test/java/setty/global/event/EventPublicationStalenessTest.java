package setty.global.event;

import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.modulith.events.EventPublication.Status;
import org.springframework.modulith.events.core.DefaultEventPublicationRegistry;
import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.modulith.events.core.TargetEventPublication;

/** 중단 복구에 사용하는 Modulith의 시간 판정 계약을 검증한다. 실제 JVM 종료나 JDBC 동시성 시험은 아니다. */
class EventPublicationStalenessTest {

    private static final Instant NOW = Instant.parse("2026-10-29T00:00:00Z");
    private static final Duration TIMEOUT = Duration.ofMinutes(5);

    private final EventPublicationRepository repository = mock(EventPublicationRepository.class);
    private final DefaultEventPublicationRegistry registry = new DefaultEventPublicationRegistry(
            repository, Clock.fixed(NOW, ZoneOffset.UTC));

    @ParameterizedTest
    @EnumSource(value = Status.class, names = {"PUBLISHED", "PROCESSING", "RESUBMITTED"})
    void 중단되어_제한시간을_넘긴_발행을_실패로_전환한다(final Status status) {
        final TargetEventPublication publication = publication(NOW.minus(TIMEOUT).minusSeconds(1), null);
        when(repository.findByStatus(status)).thenReturn(List.of(publication));

        registry.markStalePublicationsFailed(ignored -> TIMEOUT);

        verify(repository).markFailed(publication.getIdentifier());
    }

    @ParameterizedTest
    @EnumSource(value = Status.class, names = {"PUBLISHED", "PROCESSING", "RESUBMITTED"})
    void 오래전에_발행했어도_최근_재시도한_작업은_실패로_바꾸지_않는다(final Status status) {
        final TargetEventPublication publication = publication(NOW.minus(Duration.ofHours(1)), NOW.minusSeconds(30));
        when(repository.findByStatus(status)).thenReturn(List.of(publication));

        registry.markStalePublicationsFailed(ignored -> TIMEOUT);

        verify(repository, never()).markFailed(publication.getIdentifier());
    }

    @Test
    void 제한시간이_0인_상태는_감시하지_않는다() {
        registry.markStalePublicationsFailed(ignored -> Duration.ZERO);

        verifyNoInteractions(repository);
    }

    private TargetEventPublication publication(final Instant publishedAt, final Instant resubmittedAt) {
        final TargetEventPublication publication = mock(TargetEventPublication.class, CALLS_REAL_METHODS);
        when(publication.getIdentifier()).thenReturn(UUID.randomUUID());
        when(publication.getPublicationDate()).thenReturn(publishedAt);
        when(publication.getLastResubmissionDate()).thenReturn(resubmittedAt);
        return publication;
    }
}
