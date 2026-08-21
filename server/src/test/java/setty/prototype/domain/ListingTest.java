package setty.prototype.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("프로토타입 매물")
class ListingTest {
    private static final String PHONE_NUMBER = "01000000001";

    @Test
    @DisplayName("올린 순서대로 사진 순서를 매기고 첫 사진을 대표 사진으로 쓴다")
    void ordersImagesByUploadOrder() {
        final Listing listing = listing(List.of("https://example.com/1.jpg", "https://example.com/2.jpg"));

        assertThat(listing.getImages()).extracting(ListingImage::getDisplayOrder).containsExactly(1, 2);
        assertThat(listing.thumbnailUrl()).isEqualTo("https://example.com/1.jpg");
    }

    @Test
    @DisplayName("전달된 필드만 바꾸고 나머지는 그대로 둔다")
    void updatesOnlyGivenFields() {
        final Listing listing = listing(List.of("https://example.com/1.jpg"));

        listing.update("원목 책상 급처", null, null, false);

        assertThat(listing.getTitle()).isEqualTo("원목 책상 급처");
        assertThat(listing.isCanHelpMove()).isFalse();
        assertThat(listing.getDescription()).isEqualTo("사용감이 조금 있습니다.");
        assertThat(listing.getPickupTimeText()).isEqualTo("평일 오후 7시 이후");
    }

    @Test
    @DisplayName("수정하면 수정 시각이 등록 시각보다 뒤가 된다")
    void keepsUpdatedAtNotBeforeCreatedAt() {
        final Listing listing = listing(List.of("https://example.com/1.jpg"));

        listing.update(null, null, "토요일 오전 가능", null);

        assertThat(listing.getUpdatedAt()).isAfterOrEqualTo(listing.getCreatedAt());
    }

    private Listing listing(final List<String> imageUrls) {
        return new Listing(
                new Member(PHONE_NUMBER, "hashed-password"),
                "원목 책상",
                "사용감이 조금 있습니다.",
                "평일 오후 7시 이후",
                true,
                imageUrls
        );
    }
}
