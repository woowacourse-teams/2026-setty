package setty.platform.listing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.CannotCreateTransactionException;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
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
    private static final long SELLER_ID = 201L;

    @Mock
    private ListingRepository listingRepository;

    @Mock
    private ListingImageRepository listingImageRepository;

    @Mock
    private ListingImageStorage listingImageStorage;

    @Mock
    private ListingRecorder listingRecorder;

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

    @Test
    void 등록_저장이_실패하면_올린_사진을_지운다() {
        final List<String> uploaded = List.of("setty/images/listings/uploaded.jpg");
        when(listingImageStorage.upload(anyList())).thenReturn(uploaded);
        when(listingRecorder.create(anyLong(), any(), eq(uploaded)))
                .thenThrow(new CannotCreateTransactionException("커넥션을 얻지 못함"));

        assertThatThrownBy(() -> listingService.create(SELLER_ID, createCommand()))
                .isInstanceOf(CannotCreateTransactionException.class);

        verify(listingImageStorage).deleteAll(uploaded);
    }

    @Test
    void 수정_저장이_실패하면_새로_올린_사진만_지운다() {
        final List<String> uploaded = List.of("setty/images/listings/added.jpg");
        when(listingImageStorage.upload(anyList())).thenReturn(uploaded);
        when(listingRecorder.update(anyLong(), anyLong(), any(), eq(uploaded)))
                .thenThrow(new BusinessException(ErrorCode.LISTING_UPDATE_NOT_ALLOWED));

        assertThatThrownBy(() -> listingService.update(SELLER_ID, LISTING_ID, updateCommand()))
                .isInstanceOf(BusinessException.class);

        verify(listingImageStorage).deleteAll(uploaded);
    }

    @Test
    void 수정이_성공하면_빠진_사진만_지운다() {
        final List<String> removed = List.of("setty/images/listings/removed.jpg");
        when(listingImageStorage.upload(anyList())).thenReturn(List.of("setty/images/listings/added.jpg"));
        when(listingRecorder.update(anyLong(), anyLong(), any(), anyList())).thenReturn(removed);

        listingService.update(SELLER_ID, LISTING_ID, updateCommand());

        verify(listingImageStorage).deleteAll(removed);
    }

    private ListingCreateCommand createCommand() {
        return new ListingCreateCommand(
                "가상 책상", "테스트용 가상 매물입니다.", 100_000, ListingCategory.DESK, ConditionGrade.A,
                Dimensions.of(100, 50, 40), List.of(image()));
    }

    private ListingUpdateCommand updateCommand() {
        return new ListingUpdateCommand(
                "가상 책상", "테스트용 가상 매물입니다.", 100_000, ListingCategory.DESK, ConditionGrade.A,
                Dimensions.of(100, 50, 40), List.of(1L), List.of(image()));
    }

    private MockMultipartFile image() {
        return new MockMultipartFile("images", "test.jpg", "image/jpeg", new byte[]{1});
    }
}
