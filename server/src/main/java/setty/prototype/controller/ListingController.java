package setty.prototype.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import setty.prototype.dto.listing.CreateListingRequest;
import setty.prototype.dto.listing.CreateListingResponse;
import setty.prototype.dto.listing.ListingDetailResponse;
import setty.prototype.dto.listing.ListingListResponse;
import setty.prototype.dto.listing.UpdateListingRequest;
import setty.prototype.service.ListingService;
import setty.prototype.web.LoginMemberId;

@RestController
@RequestMapping("/api/listings")
public class ListingController {
    private final ListingService listingService;

    public ListingController(final ListingService listingService) {
        this.listingService = listingService;
    }

    @GetMapping
    public ResponseEntity<ListingListResponse> findAll() {
        return ResponseEntity.ok(listingService.findAll());
    }

    @GetMapping("/{listingId}")
    public ResponseEntity<ListingDetailResponse> findById(@PathVariable final Long listingId) {
        return ResponseEntity.ok(listingService.findById(listingId));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CreateListingResponse> create(
            @LoginMemberId final Long memberId,
            @Valid @RequestPart("request") final CreateListingRequest request,
            @RequestPart(value = "images", required = false) final List<MultipartFile> images
    ) {
        final CreateListingResponse response = listingService.create(memberId, request, images);

        return ResponseEntity.created(URI.create("/api/listings/" + response.listingId())).body(response);
    }

    @PatchMapping("/{listingId}")
    public ResponseEntity<Void> update(
            @LoginMemberId final Long memberId,
            @PathVariable final Long listingId,
            @Valid @RequestBody final UpdateListingRequest request
    ) {
        listingService.update(memberId, listingId, request);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{listingId}")
    public ResponseEntity<Void> delete(
            @LoginMemberId final Long memberId,
            @PathVariable final Long listingId
    ) {
        listingService.delete(memberId, listingId);

        return ResponseEntity.noContent().build();
    }
}
