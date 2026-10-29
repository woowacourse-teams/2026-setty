package setty.platform.listing.presentation;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import setty.global.auth.LoginMember;
import setty.platform.listing.application.ListingCreateCommand;
import setty.platform.listing.application.ListingCursor;
import setty.platform.listing.application.ListingService;
import setty.platform.listing.application.ListingUpdateCommand;
import setty.platform.listing.application.ListingView;
import setty.platform.listing.domain.ConditionGrade;
import setty.platform.listing.domain.ListingCategory;
import setty.platform.member.domain.Member;

@RestController
@RequestMapping("/api/listings")
public class ListingController {

    private final ListingService listingService;

    public ListingController(ListingService listingService) {
        this.listingService = listingService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ListingView.Created> create(
            @LoginMember Member member,
            @Valid @RequestPart("request") CreateListingRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images
    ) {
        ListingView.Created created = listingService.create(member.getId(), member.getDongCode(), member.getZipCode(), new ListingCreateCommand(
                request.title(),
                request.description(),
                request.price(),
                request.category(),
                request.conditionGrade(),
                request.dimensions().toDomain(),
                images
        ));
        return ResponseEntity
                .created(URI.create("/api/listings/" + created.listingId()))
                .body(created);
    }

    @GetMapping
    public ResponseEntity<ListingPageResponse> findAll(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Integer maxDeliveryFee,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) ListingCategory category,
            @RequestParam(required = false) List<ConditionGrade> conditionGrade,
            @RequestParam(required = false) Integer minPrice,
            @RequestParam(required = false) Integer maxPrice
    ) {
        // 페이징을 아직 사용하지 않는 클라이언트의 무조건 전체 조회 계약은 유지한다.
        if (cursor == null && size == null && maxDeliveryFee == null && query == null && category == null
                && conditionGrade == null && minPrice == null && maxPrice == null) {
            return ResponseEntity.ok(new ListingPageResponse(
                    listingService.findAvailableListings(bearerToken(authorization)), null));
        }
        ListingView.SummaryPage page = listingService.findAvailableListings(
                ListingCursor.parse(cursor),
                size == null ? ListingService.DEFAULT_PAGE_SIZE : size,
                bearerToken(authorization),
                maxDeliveryFee, query, category, conditionGrade, minPrice, maxPrice
        );
        return ResponseEntity.ok(new ListingPageResponse(page.items(), page.nextCursor()));
    }

    @GetMapping("/{listingId}")
    public ResponseEntity<ListingView.Detail> findDetail(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable Long listingId
    ) {
        return ResponseEntity.ok(listingService.findDetail(listingId, bearerToken(authorization)));
    }

    private static String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring("Bearer ".length());
    }

    @PutMapping(value = "/{listingId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> update(
            @LoginMember Member member,
            @PathVariable Long listingId,
            @Valid @RequestPart("request") UpdateListingRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images
    ) {
        listingService.update(member.getId(), listingId, new ListingUpdateCommand(
                request.title(),
                request.description(),
                request.price(),
                request.category(),
                request.conditionGrade(),
                request.dimensions().toDomain(),
                request.retainedImageIds(),
                images
        ));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{listingId}")
    public ResponseEntity<Void> delete(
            @LoginMember Member member,
            @PathVariable Long listingId
    ) {
        listingService.delete(member.getId(), listingId);
        return ResponseEntity.noContent().build();
    }
}
