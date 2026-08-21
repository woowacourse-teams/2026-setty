package setty.prototype.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.prototype.domain.Listing;
import setty.prototype.domain.ListingMessage;
import setty.prototype.dto.message.CreateMessageRequest;
import setty.prototype.dto.message.CreateMessageResponse;
import setty.prototype.dto.message.MessageListResponse;
import setty.prototype.dto.message.MessageResponse;
import setty.prototype.exception.ListingAccessDeniedException;
import setty.prototype.exception.ListingNotFoundException;
import setty.prototype.repository.ListingMessageRepository;
import setty.prototype.repository.ListingRepository;

@Service
public class ListingMessageService {
    private static final Logger log = LoggerFactory.getLogger(ListingMessageService.class);

    private final ListingRepository listingRepository;
    private final ListingMessageRepository listingMessageRepository;

    public ListingMessageService(
            final ListingRepository listingRepository,
            final ListingMessageRepository listingMessageRepository
    ) {
        this.listingRepository = listingRepository;
        this.listingMessageRepository = listingMessageRepository;
    }

    @Transactional
    public CreateMessageResponse create(final Long listingId, final CreateMessageRequest request) {
        final Listing listing = listingRepository.findById(listingId)
                .orElseThrow(ListingNotFoundException::new);
        final ListingMessage message = listingMessageRepository.save(
                new ListingMessage(listing, request.content())
        );

        log.info("프로토타입 쪽지 접수 완료. listingId={}, messageId={}", listingId, message.getId());

        return CreateMessageResponse.from(message);
    }

    @Transactional(readOnly = true)
    public MessageListResponse findAllByListing(final Long memberId, final Long listingId) {
        final Listing listing = listingRepository.findById(listingId)
                .orElseThrow(ListingNotFoundException::new);
        if (!listing.isOwnedBy(memberId)) {
            throw ListingAccessDeniedException.forMessages();
        }

        return new MessageListResponse(
                listingId,
                listingMessageRepository.findAllByListingIdOrderByIdDesc(listingId).stream()
                        .map(MessageResponse::from)
                        .toList()
        );
    }
}
