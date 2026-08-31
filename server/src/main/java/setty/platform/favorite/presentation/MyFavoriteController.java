package setty.platform.favorite.presentation;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import setty.global.auth.LoginMember;
import setty.platform.favorite.application.FavoriteService;
import setty.platform.listing.application.ListingView;
import setty.platform.listing.presentation.ListingListResponse;
import setty.platform.member.domain.Member;

@RestController
@RequestMapping("/api/me/favorites")
public class MyFavoriteController {

    private final FavoriteService favoriteService;

    public MyFavoriteController(final FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @GetMapping
    public ResponseEntity<ListingListResponse<ListingView.Summary>> findMine(@LoginMember final Member member) {
        return ResponseEntity.ok(new ListingListResponse<>(favoriteService.findMine(member.getId())));
    }

    @GetMapping("/{listingId}")
    public ResponseEntity<FavoriteStatusResponse> isFavorited(
            @LoginMember final Member member,
            @PathVariable final Long listingId
    ) {
        return ResponseEntity.ok(
                new FavoriteStatusResponse(favoriteService.isFavorited(member.getId(), listingId)));
    }

    @PutMapping("/{listingId}")
    public ResponseEntity<Void> add(
            @LoginMember final Member member,
            @PathVariable final Long listingId
    ) {
        favoriteService.add(member.getId(), listingId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{listingId}")
    public ResponseEntity<Void> remove(
            @LoginMember final Member member,
            @PathVariable final Long listingId
    ) {
        favoriteService.remove(member.getId(), listingId);
        return ResponseEntity.noContent().build();
    }
}
