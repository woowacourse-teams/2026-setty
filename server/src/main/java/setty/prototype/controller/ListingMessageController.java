package setty.prototype.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import setty.prototype.dto.message.CreateMessageRequest;
import setty.prototype.dto.message.CreateMessageResponse;
import setty.prototype.dto.message.MessageListResponse;
import setty.prototype.service.ListingMessageService;
import setty.prototype.web.LoginMemberId;

@RestController
@RequestMapping("/api/listings/{listingId}/messages")
public class ListingMessageController {
    private final ListingMessageService listingMessageService;

    public ListingMessageController(final ListingMessageService listingMessageService) {
        this.listingMessageService = listingMessageService;
    }

    @PostMapping
    public ResponseEntity<CreateMessageResponse> create(
            @PathVariable final Long listingId,
            @Valid @RequestBody final CreateMessageRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(listingMessageService.create(listingId, request));
    }

    @GetMapping
    public ResponseEntity<MessageListResponse> findAll(
            @LoginMemberId final Long memberId,
            @PathVariable final Long listingId
    ) {
        return ResponseEntity.ok(listingMessageService.findAllByListing(memberId, listingId));
    }
}
