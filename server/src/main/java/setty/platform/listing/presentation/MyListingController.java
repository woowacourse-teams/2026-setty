package setty.platform.listing.presentation;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.platform.listing.application.ListingService;
import setty.platform.listing.application.ListingView;

@RestController
@RequestMapping("/api/me/listings")
public class MyListingController {

    private static final String LOGIN_MEMBER_ID = "memberId";

    private final ListingService listingService;

    public MyListingController(ListingService listingService) {
        this.listingService = listingService;
    }

    @GetMapping
    public ResponseEntity<ListingListResponse<ListingView.Mine>> findMine(
            @SessionAttribute(value = LOGIN_MEMBER_ID, required = false) Long memberId
    ) {
        if (memberId == null) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        return ResponseEntity.ok(new ListingListResponse<>(listingService.findMine(memberId)));
    }
}
