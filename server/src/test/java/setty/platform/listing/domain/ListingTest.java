package setty.platform.listing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

class ListingTest {

    private static final Long SELLER_ID = 101L;

    @DisplayName("매물을 생성하면 문자열을 정리하고 배송비와 최초 상태를 결정한다")
    @Test
    void createsListingWithCalculatedValuesAndInitialState() {
        Dimensions dimensions = Dimensions.of(100, 50, 20);

        Listing listing = Listing.create(
                SELLER_ID,
                "  원목 의자  ",
                "  가상 판매용 설명입니다.  ",
                40_000,
                ListingCategory.CHAIR,
                ConditionGrade.A,
                dimensions
        );

        assertThat(listing.getSellerId()).isEqualTo(SELLER_ID);
        assertThat(listing.getTitle()).isEqualTo("원목 의자");
        assertThat(listing.getDescription()).isEqualTo("가상 판매용 설명입니다.");
        assertThat(listing.getPrice()).isEqualTo(40_000);
        assertThat(listing.getDeliveryFee()).isEqualTo(10_000);
        assertThat(listing.getTotalPrice()).isEqualTo(50_000);
        assertThat(listing.getCategory()).isEqualTo(ListingCategory.CHAIR);
        assertThat(listing.getConditionGrade()).isEqualTo(ConditionGrade.A);
        assertThat(listing.getDimensions()).isSameAs(dimensions);
        assertThat(listing.getSaleStatus()).isEqualTo(SaleStatus.AVAILABLE);
        assertThat(listing.hasPurchaseRequest()).isFalse();
        assertThat(listing.canUpdate()).isTrue();
        assertThat(listing.canDelete()).isTrue();
        assertThat(listing.isDeleted()).isFalse();
        assertThat(listing.getCreatedAt()).isNotNull();
        assertThat(listing.getUpdatedAt()).isNotNull();
    }

    @DisplayName("필수 생성값이 유효하지 않으면 매물을 생성할 수 없다")
    @Test
    void rejectsInvalidCreationValues() {
        assertBusinessError(
                () -> Listing.create(
                        null,
                        "원목 의자",
                        "가상 판매용 설명입니다.",
                        40_000,
                        ListingCategory.CHAIR,
                        ConditionGrade.A,
                        Dimensions.of(100, 50, 20)
                ),
                ErrorCode.INVALID_REQUEST
        );
        assertBusinessError(
                () -> Listing.create(
                        SELLER_ID,
                        "   ",
                        "가상 판매용 설명입니다.",
                        40_000,
                        ListingCategory.CHAIR,
                        ConditionGrade.A,
                        Dimensions.of(100, 50, 20)
                ),
                ErrorCode.INVALID_REQUEST
        );
        assertBusinessError(
                () -> Listing.create(
                        SELLER_ID,
                        "원목 의자",
                        "가상 판매용 설명입니다.",
                        -1,
                        ListingCategory.CHAIR,
                        ConditionGrade.A,
                        Dimensions.of(100, 50, 20)
                ),
                ErrorCode.INVALID_REQUEST
        );
    }

    @DisplayName("구매 신청 전에는 매물 정보와 배송비를 수정할 수 있다")
    @Test
    void updatesListingAndRecalculatesDeliveryFee() {
        Listing listing = createListing();
        Dimensions updatedDimensions = Dimensions.of(1_000, 1_000, 2);

        listing.update(
                "  확장형 식탁  ",
                "  가상 판매용 수정 설명입니다.  ",
                90_000,
                ListingCategory.TABLE,
                ConditionGrade.B,
                updatedDimensions
        );

        assertThat(listing.getTitle()).isEqualTo("확장형 식탁");
        assertThat(listing.getDescription()).isEqualTo("가상 판매용 수정 설명입니다.");
        assertThat(listing.getPrice()).isEqualTo(90_000);
        assertThat(listing.getDeliveryFee()).isEqualTo(30_000);
        assertThat(listing.getTotalPrice()).isEqualTo(120_000);
        assertThat(listing.getCategory()).isEqualTo(ListingCategory.TABLE);
        assertThat(listing.getConditionGrade()).isEqualTo(ConditionGrade.B);
        assertThat(listing.getDimensions()).isSameAs(updatedDimensions);
    }

    @DisplayName("첫 구매 신청 이후에는 추가 구매 신청과 수정·삭제를 금지한다")
    @Test
    void locksListingAfterFirstPurchaseRequest() {
        Listing listing = createListing();

        listing.registerPurchaseRequest();
        assertThat(listing.hasPurchaseRequest()).isTrue();
        assertThat(listing.canUpdate()).isFalse();
        assertThat(listing.canDelete()).isFalse();

        assertBusinessError(listing::registerPurchaseRequest, ErrorCode.ALREADY_ORDERED);

        assertBusinessError(
                () -> listing.update(
                        "수정할 수 없는 매물",
                        "가상 판매용 설명입니다.",
                        10_000,
                        ListingCategory.CHAIR,
                        ConditionGrade.B,
                        Dimensions.of(10, 10, 10)
                ),
                ErrorCode.LISTING_UPDATE_NOT_ALLOWED
        );
        assertBusinessError(listing::softDelete, ErrorCode.LISTING_DELETE_NOT_ALLOWED);
    }

    @DisplayName("구매 신청이 있는 매물만 한 번 예약되고 예약된 매물만 판매 완료된다")
    @Test
    void transitionsFromAvailableToReservedToSold() {
        Listing listing = createListing();

        assertBusinessError(listing::reserve, ErrorCode.INVALID_LISTING_STATUS_TRANSITION);
        assertBusinessError(listing::completeSale, ErrorCode.INVALID_LISTING_STATUS_TRANSITION);

        listing.registerPurchaseRequest();

        assertThat(listing.reserve()).isTrue();
        assertThat(listing.getSaleStatus()).isEqualTo(SaleStatus.RESERVED);
        assertThat(listing.reserve()).isFalse();
        assertBusinessError(listing::registerPurchaseRequest, ErrorCode.LISTING_NOT_AVAILABLE);

        listing.completeSale();

        assertThat(listing.getSaleStatus()).isEqualTo(SaleStatus.SOLD);
        listing.completeSale();
        assertThat(listing.getSaleStatus()).isEqualTo(SaleStatus.SOLD);
        assertThat(listing.reserve()).isFalse();
    }

    @DisplayName("구매 신청이 없는 판매 가능 매물은 soft delete할 수 있고 이후 모든 변경이 막힌다")
    @Test
    void softDeletesEditableListingAndBlocksFurtherChanges() {
        Listing listing = createListing();

        listing.softDelete();

        assertThat(listing.isDeleted()).isTrue();
        assertThat(listing.getDeletedAt()).isNotNull();
        assertThat(listing.canUpdate()).isFalse();
        assertThat(listing.canDelete()).isFalse();
        assertThat(listing.reserve()).isFalse();
        assertBusinessError(listing::registerPurchaseRequest, ErrorCode.LISTING_NOT_AVAILABLE);
        assertBusinessError(listing::softDelete, ErrorCode.LISTING_DELETE_NOT_ALLOWED);
    }

    @DisplayName("판매자 식별자는 매물 소유 여부를 결정한다")
    @Test
    void checksOwnershipBySellerId() {
        Listing listing = createListing();

        assertThat(listing.isOwnedBy(SELLER_ID)).isTrue();
        assertThat(listing.isOwnedBy(202L)).isFalse();
        assertThat(listing.isOwnedBy(null)).isFalse();
    }

    private static Listing createListing() {
        return Listing.create(
                SELLER_ID,
                "원목 의자",
                "가상 판매용 설명입니다.",
                40_000,
                ListingCategory.CHAIR,
                ConditionGrade.A,
                Dimensions.of(100, 50, 20)
        );
    }

    private static void assertBusinessError(Runnable action, ErrorCode expectedErrorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(expectedErrorCode)
                );
    }
}
