package setty.platform.listing.presentation;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import setty.global.auth.LoginMember;
import setty.platform.listing.application.ListingCreateCommand;
import setty.platform.listing.application.ListingService;
import setty.platform.listing.application.ListingUpdateCommand;
import setty.platform.listing.application.ListingView;
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
        ListingView.Created created = listingService.create(member.getId(), new ListingCreateCommand(
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
    public ResponseEntity<ListingListResponse<ListingView.Summary>> findAll() {
        return ResponseEntity.ok(new ListingListResponse<>(listingService.findAvailableListings()));
    }

    @GetMapping("/{listingId}")
    public ResponseEntity<ListingView.Detail> findDetail(@PathVariable Long listingId) {
        return ResponseEntity.ok(listingService.findDetail(listingId));
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
