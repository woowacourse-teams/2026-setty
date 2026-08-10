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
import setty.estimate.application.command.CreateEstimateRequestCommand;
import setty.estimate.domain.EstimateRequest;
import setty.estimate.domain.EstimateRequestRepository;
import setty.estimate.domain.EstimateRequestStatus;

@ExtendWith(MockitoExtension.class)
class EstimateRequestCreateServiceTest {
    @Mock
    private EstimateRequestRepository estimateRequestRepository;

    @Captor
    private ArgumentCaptor<EstimateRequest> estimateRequestCaptor;

    @Test
    void savesAnEstimateRequestInPendingReviewStatus() {
        when(estimateRequestRepository.save(any(EstimateRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        final EstimateRequestCreateService service = new EstimateRequestCreateService(estimateRequestRepository);

        service.create(new CreateEstimateRequestCommand(
                "테스트사용자",
                "01000000000",
                "서울성북구",
                "원목의자",
                false,
                "https://www.daangn.com/articles/test-1"
        ));

        verify(estimateRequestRepository).save(estimateRequestCaptor.capture());
        final EstimateRequest savedEstimateRequest = estimateRequestCaptor.getValue();
        assertThat(savedEstimateRequest.getPhoneNumber()).isEqualTo("01000000000");
        assertThat(savedEstimateRequest.getProductLink()).isEqualTo("https://www.daangn.com/articles/test-1");
        assertThat(savedEstimateRequest.getStatus()).isEqualTo(EstimateRequestStatus.PENDING_REVIEW);
        assertThat(savedEstimateRequest.getCreatedAt()).isNotNull();
    }
}
