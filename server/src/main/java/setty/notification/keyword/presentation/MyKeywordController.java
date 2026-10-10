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
import setty.platform.listing.presentation.ListingListResponse;
import setty.platform.member.domain.Member;

@RestController
@RequestMapping("/api/me/keywords")
public class MyKeywordController {

    private final KeywordSubscriptionService keywordSubscriptionService;

    public MyKeywordController(final KeywordSubscriptionService keywordSubscriptionService) {
        this.keywordSubscriptionService = keywordSubscriptionService;
    }

    @GetMapping
    public ResponseEntity<ListingListResponse<KeywordResponse>> findMine(@LoginMember final Member member) {
        final List<KeywordResponse> keywords = keywordSubscriptionService.findMine(member.getId()).stream()
                .map(KeywordResponse::from)
                .toList();
        return ResponseEntity.ok(new ListingListResponse<>(keywords));
    }

    @PutMapping
    public ResponseEntity<Void> subscribe(
            @LoginMember final Member member,
            @Valid @RequestBody final KeywordRequest request
    ) {
        keywordSubscriptionService.subscribe(member.getId(), request.keyword());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{subscriptionId}")
    public ResponseEntity<Void> unsubscribe(
            @LoginMember final Member member,
            @PathVariable final Long subscriptionId
    ) {
        keywordSubscriptionService.unsubscribe(member.getId(), subscriptionId);
        return ResponseEntity.noContent().build();
    }
}
