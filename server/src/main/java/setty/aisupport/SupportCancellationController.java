package setty.aisupport;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import setty.global.auth.LoginMember;

import java.util.Map;

@RestController
@RequestMapping("/api/support/cancellations")
@RequiredArgsConstructor
class SupportCancellationController {
    private final SupportCancellationResults results;

    @GetMapping("/{requestId}")
    SupportCancellationResults.Outcome get(
            @LoginMember Long member, @PathVariable String requestId) {
        return results.get(member, requestId);
    }

    @ExceptionHandler(SupportException.class)
    ResponseEntity<Map<String, String>> error(SupportException e) {
        return ResponseEntity.status(e.status)
                .body(OrderedJson.of("code", e.code, "message", e.getMessage()));
    }
}
