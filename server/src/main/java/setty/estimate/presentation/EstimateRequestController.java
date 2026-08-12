package setty.estimate.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import setty.estimate.application.EstimateRequestCreateService;
import setty.estimate.application.command.CreateEstimateRequestCommand;
import setty.estimate.application.result.CreatedEstimateRequest;
import setty.estimate.presentation.payload.CreateEstimateRequestRequest;
import setty.estimate.presentation.payload.EstimateRequestCreateResponse;

@RestController
@RequestMapping("/api/estimate-requests")
@RequiredArgsConstructor
public class EstimateRequestController {
    private final EstimateRequestCreateService estimateRequestCreateService;

    @PostMapping
    public ResponseEntity<EstimateRequestCreateResponse> create(
            @Valid @RequestBody final CreateEstimateRequestRequest request
    ) {
        final CreatedEstimateRequest createdEstimateRequest = estimateRequestCreateService.create(
                new CreateEstimateRequestCommand(
                        request.name(),
                        request.phoneNumber(),
                        request.tradeArea(),
                        request.itemType(),
                        request.highValueItem(),
                        request.productLink(),
                        request.privacyConsent(),
                        request.privacyPolicyVersion()
                )
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(EstimateRequestCreateResponse.from(createdEstimateRequest));
    }
}
