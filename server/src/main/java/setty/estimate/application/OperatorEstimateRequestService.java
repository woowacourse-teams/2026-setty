package setty.estimate.application;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.estimate.application.command.RecordManualNotificationCommand;
import setty.estimate.application.exception.EstimateRequestNotFoundException;
import setty.estimate.application.query.EstimateRequestDetail;
import setty.estimate.application.query.EstimateRequestSummary;
import setty.estimate.application.query.ManualNotificationResult;
import setty.estimate.domain.EstimateRequest;
import setty.estimate.domain.EstimateRequestRepository;
import setty.estimate.domain.ManualNotification;
import setty.estimate.domain.ManualNotificationRepository;

@Service
@RequiredArgsConstructor
public class OperatorEstimateRequestService {
    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final EstimateRequestRepository estimateRequestRepository;
    private final ManualNotificationRepository manualNotificationRepository;

    @Transactional(readOnly = true)
    public List<EstimateRequestSummary> findAll() {
        return estimateRequestRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public EstimateRequestDetail findById(final Long estimateRequestId) {
        final EstimateRequest estimateRequest = findEstimateRequest(estimateRequestId);
        final ManualNotificationResult manualNotification = manualNotificationRepository
                .findByEstimateRequestId(estimateRequestId)
                .map(this::toManualNotificationResult)
                .orElse(null);

        return new EstimateRequestDetail(
                estimateRequest.getId(),
                estimateRequest.getName(),
                estimateRequest.getPhoneNumber(),
                estimateRequest.getTradeArea(),
                estimateRequest.getItemType(),
                estimateRequest.isHighValueItem(),
                estimateRequest.getStatus(),
                toOffsetDateTime(estimateRequest.getCreatedAt()),
                manualNotification
        );
    }

    @Transactional
    public void recordManualNotification(
            final Long estimateRequestId,
            final RecordManualNotificationCommand command
    ) {
        final EstimateRequest estimateRequest = findEstimateRequest(estimateRequestId);

        manualNotificationRepository.findByEstimateRequestId(estimateRequestId)
                .ifPresentOrElse(
                        manualNotification -> manualNotification.update(
                                command.messageContent(),
                                command.transportFeasible()
                        ),
                        () -> {
                            estimateRequest.markEstimateNotified();
                            manualNotificationRepository.save(ManualNotification.create(
                                    estimateRequestId,
                                    command.messageContent(),
                                    command.transportFeasible()
                            ));
                        }
                );
    }

    private EstimateRequest findEstimateRequest(final Long estimateRequestId) {
        return estimateRequestRepository.findById(estimateRequestId)
                .orElseThrow(EstimateRequestNotFoundException::new);
    }

    private EstimateRequestSummary toSummary(final EstimateRequest estimateRequest) {
        return new EstimateRequestSummary(
                estimateRequest.getId(),
                estimateRequest.getTradeArea(),
                estimateRequest.getItemType(),
                estimateRequest.isHighValueItem(),
                estimateRequest.getStatus(),
                toOffsetDateTime(estimateRequest.getCreatedAt())
        );
    }

    private ManualNotificationResult toManualNotificationResult(final ManualNotification manualNotification) {
        return new ManualNotificationResult(
                manualNotification.getMessageContent(),
                manualNotification.isTransportFeasible()
        );
    }

    private OffsetDateTime toOffsetDateTime(final LocalDateTime dateTime) {
        return dateTime.atZone(SEOUL_ZONE_ID).toOffsetDateTime();
    }
}
