package setty.estimate.presentation;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import setty.estimate.application.OperatorEstimateRequestService;
import setty.estimate.application.command.RecordManualNotificationCommand;
import setty.estimate.application.query.EstimateRequestDetail;
import setty.estimate.presentation.payload.EstimateRequestDetailResponse;
import setty.estimate.presentation.payload.EstimateRequestListResponse;
import setty.estimate.presentation.payload.ManualNotificationRequest;

@RestController
@RequestMapping("/api/operator/estimate-requests")
@RequiredArgsConstructor
public class OperatorEstimateRequestController {
    private final OperatorEstimateRequestService operatorEstimateRequestService;

    @GetMapping
    public List<EstimateRequestListResponse> findAll() {
        return operatorEstimateRequestService.findAll().stream()
                .map(EstimateRequestListResponse::from)
                .toList();
    }

    @GetMapping("/{estimateRequestId}")
    public EstimateRequestDetailResponse findById(@PathVariable final Long estimateRequestId) {
        final EstimateRequestDetail estimateRequestDetail = operatorEstimateRequestService.findById(estimateRequestId);
        return EstimateRequestDetailResponse.from(estimateRequestDetail);
    }

    @PutMapping("/{estimateRequestId}/manual-notification")
    public ResponseEntity<Void> recordManualNotification(
            @PathVariable final Long estimateRequestId,
            @Valid @RequestBody final ManualNotificationRequest request
    ) {
        operatorEstimateRequestService.recordManualNotification(
                estimateRequestId,
                new RecordManualNotificationCommand(
                        request.messageContent(),
                        request.transportFeasible()
                )
        );

        return ResponseEntity.noContent().build();
    }
}
