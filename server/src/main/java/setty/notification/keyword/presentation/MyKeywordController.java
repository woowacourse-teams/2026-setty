package setty.notification.keyword.presentation;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import setty.global.auth.LoginMember;
import setty.notification.keyword.application.KeywordSubscriptionService;
import setty.notification.presentation.ListResponse;

@RestController
@RequestMapping("/api/me/keywords")
public class MyKeywordController {

    private final KeywordSubscriptionService keywordSubscriptionService;

    public MyKeywordController(final KeywordSubscriptionService keywordSubscriptionService) {
        this.keywordSubscriptionService = keywordSubscriptionService;
    }

    @GetMapping
    public ResponseEntity<ListResponse<KeywordResponse>> findMine(@LoginMember final Long memberId) {
        final List<KeywordResponse> keywords = keywordSubscriptionService.findMine(memberId).stream()
                .map(KeywordResponse::from)
                .toList();
        return ResponseEntity.ok(new ListResponse<>(keywords));
    }

    @PutMapping
    public ResponseEntity<Void> subscribe(
            @LoginMember final Long memberId,
            @Valid @RequestBody final KeywordRequest request
    ) {
        keywordSubscriptionService.subscribe(memberId, request.keyword());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{subscriptionId}/conditions")
    public ResponseEntity<Void> updateConditions(
            @LoginMember final Long memberId,
            @PathVariable final Long subscriptionId,
            @RequestBody final KeywordConditionsRequest request
    ) {
        keywordSubscriptionService.updateConditions(memberId, subscriptionId, request.toConditions());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{subscriptionId}")
    public ResponseEntity<Void> unsubscribe(
            @LoginMember final Long memberId,
            @PathVariable final Long subscriptionId
    ) {
        keywordSubscriptionService.unsubscribe(memberId, subscriptionId);
        return ResponseEntity.noContent().build();
    }
}
