package setty.prototype.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.common.time.SeoulDateTime;
import setty.prototype.domain.Listing;
import setty.prototype.domain.Member;
import setty.prototype.dto.seller.SellerPageResponse;
import setty.prototype.exception.AuthenticationRequiredException;
import setty.prototype.repository.ListingMessageRepository;
import setty.prototype.repository.ListingMessageStat;
import setty.prototype.repository.ListingRepository;
import setty.prototype.repository.MemberRepository;

@Service
public class SellerPageService {
    private final MemberRepository memberRepository;
    private final ListingRepository listingRepository;
    private final ListingMessageRepository listingMessageRepository;

    public SellerPageService(
            final MemberRepository memberRepository,
            final ListingRepository listingRepository,
            final ListingMessageRepository listingMessageRepository
    ) {
        this.memberRepository = memberRepository;
        this.listingRepository = listingRepository;
        this.listingMessageRepository = listingMessageRepository;
    }

    @Transactional(readOnly = true)
    public SellerPageResponse findByMemberId(final Long memberId) {
        final Member seller = memberRepository.findById(memberId)
                .orElseThrow(AuthenticationRequiredException::new);
        final List<Listing> listings = listingRepository.findAllBySellerIdOrderByCreatedAtDescIdDesc(memberId);
        final Map<Long, ListingMessageStat> statsByListingId = findStatsByListingId(listings);

        final List<SellerPageResponse.SellerListing> sellerListings = listings.stream()
                .map(listing -> toSellerListing(listing, statsByListingId.get(listing.getId())))
                .toList();
        final long totalMessageCount = sellerListings.stream()
                .mapToLong(SellerPageResponse.SellerListing::messageCount)
                .sum();

        return new SellerPageResponse(
                new SellerPageResponse.Seller(seller.getPhoneNumber()),
                new SellerPageResponse.Summary(sellerListings.size(), totalMessageCount),
                sellerListings
        );
    }

    private Map<Long, ListingMessageStat> findStatsByListingId(final List<Listing> listings) {
        if (listings.isEmpty()) {
            return Map.of();
        }
        final List<Long> listingIds = listings.stream()
                .map(Listing::getId)
                .toList();

        return listingMessageRepository.findStatsByListingIds(listingIds).stream()
                .collect(Collectors.toMap(ListingMessageStat::getListingId, Function.identity()));
    }

    private SellerPageResponse.SellerListing toSellerListing(final Listing listing, final ListingMessageStat stat) {
        return new SellerPageResponse.SellerListing(
                listing.getId(),
                listing.getTitle(),
                listing.thumbnailUrl(),
                listing.getPickupTimeText(),
                listing.isCanHelpMove(),
                stat == null ? 0 : stat.getMessageCount(),
                stat == null ? null : SeoulDateTime.toOffsetDateTime(stat.getLatestMessageAt()),
                SeoulDateTime.toOffsetDateTime(listing.getCreatedAt())
        );
    }
}
