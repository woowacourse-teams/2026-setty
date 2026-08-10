package setty.estimate.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import setty.estimate.application.command.RecordManualNotificationCommand;
import setty.estimate.application.exception.EstimateRequestNotFoundException;
import setty.estimate.domain.EstimateRequest;
import setty.estimate.domain.EstimateRequestRepository;
import setty.estimate.domain.EstimateRequestStatus;
import setty.estimate.domain.InvalidEstimateRequestStatusException;
import setty.estimate.domain.ManualNotification;
import setty.estimate.domain.ManualNotificationRepository;

@ExtendWith(MockitoExtension.class)
class OperatorEstimateRequestServiceTest {
    @Mock
    private EstimateRequestRepository estimateRequestRepository;

    @Mock
    private ManualNotificationRepository manualNotificationRepository;

    @Captor
    private ArgumentCaptor<ManualNotification> manualNotificationCaptor;

    @Test
    void recordsManualNotificationAndChangesStatusTogether() {
        final EstimateRequest estimateRequest = EstimateRequest.pendingReview(
                "테스트사용자",
                "01000000000",
                "서울성북구",
                "원목의자",
                false
        );
        when(estimateRequestRepository.findById(1L)).thenReturn(Optional.of(estimateRequest));
        when(manualNotificationRepository.findByEstimateRequestId(1L)).thenReturn(Optional.empty());
        when(manualNotificationRepository.save(any(ManualNotification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        final OperatorEstimateRequestService service = new OperatorEstimateRequestService(
                estimateRequestRepository,
                manualNotificationRepository
        );

        service.recordManualNotification(1L, new RecordManualNotificationCommand(
                "예상 운송비는 30000원입니다.",
                true
        ));

        verify(manualNotificationRepository).save(manualNotificationCaptor.capture());
        final ManualNotification savedManualNotification = manualNotificationCaptor.getValue();
        assertThat(estimateRequest.getStatus()).isEqualTo(EstimateRequestStatus.ESTIMATE_NOTIFIED);
        assertThat(savedManualNotification.getEstimateRequestId()).isEqualTo(1L);
        assertThat(savedManualNotification.getMessageContent()).isEqualTo("예상 운송비는 30000원입니다.");
        assertThat(savedManualNotification.isTransportFeasible()).isTrue();
        assertThat(savedManualNotification.getNotifiedAt()).isNotNull();
    }

    @Test
    void updatesTheExistingManualNotificationWithoutCreatingANewOne() {
        final EstimateRequest estimateRequest = EstimateRequest.pendingReview(
                "테스트사용자",
                "01000000000",
                "서울성북구",
                "원목의자",
                false
        );
        estimateRequest.markEstimateNotified();
        final ManualNotification existingManualNotification = ManualNotification.create(
                1L,
                "처음 보낸 테스트 문자입니다.",
                true
        );
        when(estimateRequestRepository.findById(1L)).thenReturn(Optional.of(estimateRequest));
        when(manualNotificationRepository.findByEstimateRequestId(1L))
                .thenReturn(Optional.of(existingManualNotification));
        final OperatorEstimateRequestService service = new OperatorEstimateRequestService(
                estimateRequestRepository,
                manualNotificationRepository
        );

        service.recordManualNotification(1L, new RecordManualNotificationCommand(
                "정정해서 다시 보낸 테스트 문자입니다.",
                false
        ));

        verify(manualNotificationRepository, never()).save(any(ManualNotification.class));
        assertThat(estimateRequest.getStatus()).isEqualTo(EstimateRequestStatus.ESTIMATE_NOTIFIED);
        assertThat(existingManualNotification.getMessageContent())
                .isEqualTo("정정해서 다시 보낸 테스트 문자입니다.");
        assertThat(existingManualNotification.isTransportFeasible()).isFalse();
    }

    @Test
    void rejectsCreatingANotificationForAnAlreadyNotifiedRequestWithoutARecord() {
        final EstimateRequest estimateRequest = EstimateRequest.pendingReview(
                "테스트사용자",
                "01000000000",
                "서울성북구",
                "원목의자",
                false
        );
        estimateRequest.markEstimateNotified();
        when(estimateRequestRepository.findById(1L)).thenReturn(Optional.of(estimateRequest));
        when(manualNotificationRepository.findByEstimateRequestId(1L)).thenReturn(Optional.empty());
        final OperatorEstimateRequestService service = new OperatorEstimateRequestService(
                estimateRequestRepository,
                manualNotificationRepository
        );

        assertThatThrownBy(() -> service.recordManualNotification(1L, new RecordManualNotificationCommand(
                "이미 안내한 내용입니다.",
                false
        )))
                .isInstanceOf(InvalidEstimateRequestStatusException.class);
    }

    @Test
    void throwsNotFoundWhenTheEstimateRequestDoesNotExist() {
        when(estimateRequestRepository.findById(99L)).thenReturn(Optional.empty());
        final OperatorEstimateRequestService service = new OperatorEstimateRequestService(
                estimateRequestRepository,
                manualNotificationRepository
        );

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(EstimateRequestNotFoundException.class);
    }
}
