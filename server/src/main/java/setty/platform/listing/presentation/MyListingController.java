package setty.platform.listing.presentation;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import setty.global.auth.LoginMember;
import setty.platform.listing.application.ListingService;
import setty.platform.listing.application.ListingView;
import setty.platform.member.domain.Member;

@RestController
@RequestMapping("/api/me/listings")
public class MyListingController {

    private final ListingService listingService;

    public MyListingController(ListingService listingService) {
        this.listingService = listingService;
    }

    @GetMapping
    public ResponseEntity<ListingListResponse<ListingView.Mine>> findMine(
            @LoginMember Member member
    ) {
        return ResponseEntity.ok(new ListingListResponse<>(listingService.findMine(member.getId())));
    }
}
