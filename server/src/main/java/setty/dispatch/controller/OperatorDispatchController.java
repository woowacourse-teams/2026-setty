package setty.dispatch.controller;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import setty.dispatch.domain.DispatchStatus;
import setty.dispatch.dto.operator.OperatorDispatchRequestDetailResponse;
import setty.dispatch.dto.operator.OperatorDispatchRequestSummaryResponse;
import setty.dispatch.dto.operator.OperatorFinalAmountRequest;
import setty.dispatch.dto.operator.OperatorMessageRequest;
import setty.dispatch.dto.operator.OperatorFinalAmountResponse;
import setty.dispatch.service.OperatorDispatchService;

@RestController
@RequestMapping("/api/operator/dispatch-requests")
public class OperatorDispatchController {
    private final OperatorDispatchService operatorDispatchService;

    public OperatorDispatchController(final OperatorDispatchService operatorDispatchService) {
        this.operatorDispatchService = operatorDispatchService;
    }

    @GetMapping
    public ResponseEntity<List<OperatorDispatchRequestSummaryResponse>> findAll(
            @RequestParam(required = false) final DispatchStatus status
    ) {
        return ResponseEntity.ok(operatorDispatchService.findAll(status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OperatorDispatchRequestDetailResponse> findById(@PathVariable final Long id) {
        return ResponseEntity.ok(operatorDispatchService.findById(id));
    }

    @PutMapping("/{id}/final-amount")
    public ResponseEntity<OperatorFinalAmountResponse> recordFinalAmount(
            @PathVariable final Long id,
            @Valid @RequestBody final OperatorFinalAmountRequest request
    ) {
        return ResponseEntity.ok(operatorDispatchService.recordFinalAmount(id, request));
    }

    @PutMapping("/{id}/message")
    public ResponseEntity<Void> updateMessage(
            @PathVariable final Long id,
            @Valid @RequestBody final OperatorMessageRequest request
    ) {
        operatorDispatchService.updateMessageContent(id, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/completion")
    public ResponseEntity<Void> completeDispatch(@PathVariable final Long id) {
        operatorDispatchService.completeDispatch(id);
        return ResponseEntity.noContent().build();
    }
}
