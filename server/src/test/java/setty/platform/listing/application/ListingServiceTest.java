package setty.platform.listing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import setty.platform.listing.domain.ConditionGrade;
import setty.platform.listing.domain.Dimensions;
import setty.platform.listing.domain.Listing;
import setty.platform.listing.domain.ListingCategory;
import setty.platform.listing.repository.ListingImageRepository;
import setty.platform.listing.repository.ListingRepository;
import setty.platform.listing.storage.ListingImageStorage;

@ExtendWith(MockitoExtension.class)
class ListingServiceTest {

    private static final long LISTING_ID = 101L;

    @Mock
    private ListingRepository listingRepository;

    @Mock
    private ListingImageRepository listingImageRepository;

    @Mock
    private ListingImageStorage listingImageStorage;

    @InjectMocks
    private ListingService listingService;

    @Test
    void 만료된_PENDING_주문의_매물을_잠그고_구매_요청을_해제한다() {
        final Listing listing = Listing.create(
                201L,
                "가상 책상",
                "테스트용 가상 매물입니다.",
                100_000,
                ListingCategory.DESK,
                ConditionGrade.A,
                Dimensions.of(100, 50, 40)
        );
        listing.registerPurchaseRequest();
        when(listingRepository.findByIdForUpdate(LISTING_ID)).thenReturn(Optional.of(listing));

        listingService.releasePurchaseRequestForExpiredPendingOrder(LISTING_ID);

        assertThat(listing.hasPurchaseRequest()).isFalse();
        verify(listingRepository).findByIdForUpdate(LISTING_ID);
    }
}
