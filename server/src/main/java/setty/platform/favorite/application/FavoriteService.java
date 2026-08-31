package setty.platform.favorite.application;

import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.platform.favorite.domain.Favorite;
import setty.platform.favorite.repository.FavoriteRepository;
import setty.platform.listing.application.ListingService;
import setty.platform.listing.application.ListingView;
import setty.platform.listing.domain.Listing;
import setty.platform.listing.repository.ListingRepository;

@Service
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final ListingRepository listingRepository;
    private final ListingService listingService;

    public FavoriteService(
            final FavoriteRepository favoriteRepository,
            final ListingRepository listingRepository,
            final ListingService listingService
    ) {
        this.favoriteRepository = favoriteRepository;
        this.listingRepository = listingRepository;
        this.listingService = listingService;
    }

    public void add(final Long memberId, final Long listingId) {
        final Listing listing = listingRepository.findByIdAndDeletedAtIsNull(listingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND));
        if (listing.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.CANNOT_FAVORITE_OWN_LISTING);
        }
        saveIgnoringDuplicate(new Favorite(memberId, listingId));
    }

    @Transactional
    public void remove(final Long memberId, final Long listingId) {
        favoriteRepository.deleteByMemberIdAndListingId(memberId, listingId);
    }

    @Transactional(readOnly = true)
    public boolean isFavorited(final Long memberId, final Long listingId) {
        return favoriteRepository.existsByMemberIdAndListingId(memberId, listingId);
    }

    @Transactional(readOnly = true)
    public List<ListingView.Summary> findMine(final Long memberId) {
        final List<Long> listingIds = favoriteRepository.findListingIdsByMemberIdOrderByCreatedAtDesc(memberId);
        return listingService.findSummaries(listingIds);
    }

    private void saveIgnoringDuplicate(final Favorite favorite) {
        try {
            favoriteRepository.saveAndFlush(favorite);
        } catch (final DataIntegrityViolationException alreadyFavorited) {
        }
    }
}
