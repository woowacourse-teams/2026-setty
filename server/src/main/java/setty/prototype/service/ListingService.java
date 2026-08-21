package setty.prototype.service;

import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import setty.prototype.domain.Listing;
import setty.prototype.domain.Member;
import setty.prototype.dto.listing.CreateListingRequest;
import setty.prototype.dto.listing.CreateListingResponse;
import setty.prototype.dto.listing.ListingDetailResponse;
import setty.prototype.dto.listing.ListingListResponse;
import setty.prototype.dto.listing.ListingSummaryResponse;
import setty.prototype.dto.listing.UpdateListingRequest;
import setty.prototype.exception.AuthenticationRequiredException;
import setty.prototype.exception.InvalidRequestException;
import setty.prototype.exception.ListingAccessDeniedException;
import setty.prototype.exception.ListingNotFoundException;
import setty.prototype.repository.ListingMessageRepository;
import setty.prototype.repository.ListingRepository;
import setty.prototype.repository.MemberRepository;

@Service
public class ListingService {
    private static final Logger log = LoggerFactory.getLogger(ListingService.class);

    private final ListingRepository listingRepository;
    private final ListingMessageRepository listingMessageRepository;
    private final MemberRepository memberRepository;
    private final ListingImageStorage listingImageStorage;

    public ListingService(
            final ListingRepository listingRepository,
            final ListingMessageRepository listingMessageRepository,
            final MemberRepository memberRepository,
            final ListingImageStorage listingImageStorage
    ) {
        this.listingRepository = listingRepository;
        this.listingMessageRepository = listingMessageRepository;
        this.memberRepository = memberRepository;
        this.listingImageStorage = listingImageStorage;
    }

    @Transactional
    public CreateListingResponse create(
            final Long memberId,
            final CreateListingRequest request,
            final List<MultipartFile> images
    ) {
        final Member seller = memberRepository.findById(memberId)
                .orElseThrow(AuthenticationRequiredException::new);
        final List<String> imageUrls = listingImageStorage.storeAll(images);
        final Listing listing = listingRepository.save(new Listing(
                seller,
                request.title(),
                request.description(),
                request.pickupTimeText(),
                request.canHelpMove(),
                imageUrls
        ));

        log.info("프로토타입 매물 등록 완료. listingId={}, memberId={}, imageCount={}",
                listing.getId(), memberId, imageUrls.size());

        return CreateListingResponse.from(listing);
    }

    @Transactional(readOnly = true)
    public ListingListResponse findAll() {
        return new ListingListResponse(listingRepository.findAllByOrderByCreatedAtDescIdDesc().stream()
                .map(ListingSummaryResponse::from)
                .toList());
    }

    @Transactional(readOnly = true)
    public ListingDetailResponse findById(final Long listingId) {
        return ListingDetailResponse.from(listingRepository.findWithImagesById(listingId)
                .orElseThrow(ListingNotFoundException::new));
    }

    @Transactional
    public void update(final Long memberId, final Long listingId, final UpdateListingRequest request) {
        if (request.hasNoChange()) {
            throw new InvalidRequestException("변경할 값을 하나 이상 보내야 합니다.");
        }
        final Listing listing = findOwnedListing(memberId, listingId, ListingAccessDeniedException::forUpdate);
        listing.update(request.title(), request.description(), request.pickupTimeText(), request.canHelpMove());

        log.info("프로토타입 매물 수정 완료. listingId={}, memberId={}", listingId, memberId);
    }

    @Transactional
    public void delete(final Long memberId, final Long listingId) {
        final Listing listing = findOwnedListing(memberId, listingId, ListingAccessDeniedException::forDelete);
        listingMessageRepository.deleteByListingId(listingId);
        listingRepository.delete(listing);

        log.info("프로토타입 매물 삭제 완료. listingId={}, memberId={}", listingId, memberId);
    }

    private Listing findOwnedListing(
            final Long memberId,
            final Long listingId,
            final Supplier<ListingAccessDeniedException> accessDenied
    ) {
        final Listing listing = listingRepository.findById(listingId)
                .orElseThrow(ListingNotFoundException::new);
        if (!listing.isOwnedBy(memberId)) {
            throw accessDenied.get();
        }

        return listing;
    }
}
