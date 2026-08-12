package setty.estimate.application;

import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.estimate.application.command.CreateEstimateRequestCommand;
import setty.estimate.application.event.EstimateRequestCreatedEvent;
import setty.estimate.application.result.CreatedEstimateRequest;
import setty.estimate.domain.EstimateRequest;
import setty.estimate.domain.EstimateRequestRepository;

@Service
@RequiredArgsConstructor
public class EstimateRequestCreateService {
    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final EstimateRequestRepository estimateRequestRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public CreatedEstimateRequest create(final CreateEstimateRequestCommand command) {
        final EstimateRequest estimateRequest = EstimateRequest.pendingReview(
                command.name(),
                command.phoneNumber(),
                command.tradeArea(),
                command.itemType(),
                command.highValueItem(),
                command.productLink()
        );
        final EstimateRequest savedEstimateRequest = estimateRequestRepository.save(estimateRequest);
        eventPublisher.publishEvent(EstimateRequestCreatedEvent.from(savedEstimateRequest));

        return new CreatedEstimateRequest(
                savedEstimateRequest.getId(),
                savedEstimateRequest.getStatus(),
                savedEstimateRequest.getCreatedAt().atZone(SEOUL_ZONE_ID).toOffsetDateTime()
        );
    }
}
