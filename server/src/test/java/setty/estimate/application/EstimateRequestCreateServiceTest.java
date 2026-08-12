package setty.estimate.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import setty.estimate.application.command.CreateEstimateRequestCommand;
import setty.estimate.application.event.EstimateRequestCreatedEvent;
import setty.estimate.domain.EstimateRequest;
import setty.estimate.domain.EstimateRequestRepository;
import setty.estimate.domain.EstimateRequestStatus;

@ExtendWith(MockitoExtension.class)
class EstimateRequestCreateServiceTest {
    @Mock
    private EstimateRequestRepository estimateRequestRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Captor
    private ArgumentCaptor<EstimateRequest> estimateRequestCaptor;

    @Captor
    private ArgumentCaptor<EstimateRequestCreatedEvent> eventCaptor;

    @Test
    void savesAnEstimateRequestInPendingReviewStatus() {
        when(estimateRequestRepository.save(any(EstimateRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        createEstimateRequest();

        verify(estimateRequestRepository).save(estimateRequestCaptor.capture());
        final EstimateRequest savedEstimateRequest = estimateRequestCaptor.getValue();
        assertThat(savedEstimateRequest.getPhoneNumber()).isEqualTo("01000000000");
        assertThat(savedEstimateRequest.getProductLink()).isEqualTo("https://www.daangn.com/articles/test-1");
        assertThat(savedEstimateRequest.getStatus()).isEqualTo(EstimateRequestStatus.PENDING_REVIEW);
        assertThat(savedEstimateRequest.getCreatedAt()).isNotNull();
    }

    @Test
    void publishesCreatedEvent() {
        when(estimateRequestRepository.save(any(EstimateRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        createEstimateRequest();

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().itemType()).isEqualTo("원목의자");
        assertThat(eventCaptor.getValue().productLink()).isEqualTo("https://www.daangn.com/articles/test-1");
    }

    @Test
    void recordsPrivacyConsentWhenConsented() {
        when(estimateRequestRepository.save(any(EstimateRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        final EstimateRequestCreateService service =
                new EstimateRequestCreateService(estimateRequestRepository, eventPublisher);

        service.create(new CreateEstimateRequestCommand(
                "테스트사용자",
                "01000000000",
                "서울성북구",
                "원목의자",
                false,
                "https://www.daangn.com/articles/test-1",
                true,
                "2026-08-06"
        ));

        verify(estimateRequestRepository).save(estimateRequestCaptor.capture());
        assertThat(estimateRequestCaptor.getValue().getPrivacyConsentedAt()).isNotNull();
        assertThat(estimateRequestCaptor.getValue().getPrivacyPolicyVersion()).isEqualTo("2026-08-06");
    }

    @Test
    void acceptsRequestWithoutPrivacyConsentForBackwardCompatibility() {
        when(estimateRequestRepository.save(any(EstimateRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        createEstimateRequest();

        verify(estimateRequestRepository).save(estimateRequestCaptor.capture());
        assertThat(estimateRequestCaptor.getValue().getPrivacyConsentedAt()).isNull();
        assertThat(estimateRequestCaptor.getValue().getPrivacyPolicyVersion()).isNull();
    }

    private void createEstimateRequest() {
        final EstimateRequestCreateService service =
                new EstimateRequestCreateService(estimateRequestRepository, eventPublisher);

        service.create(new CreateEstimateRequestCommand(
                "테스트사용자",
                "01000000000",
                "서울성북구",
                "원목의자",
                false,
                "https://www.daangn.com/articles/test-1",
                null,
                null
        ));
    }
}
