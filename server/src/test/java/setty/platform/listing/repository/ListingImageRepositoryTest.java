package setty.platform.listing.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import setty.platform.listing.domain.ConditionGrade;
import setty.platform.listing.domain.Dimensions;
import setty.platform.listing.domain.Listing;
import setty.platform.listing.domain.ListingCategory;
import setty.platform.listing.domain.ListingImage;
import setty.support.MySqlIntegrationTestSupport;

@SpringBootTest
@Transactional
class ListingImageRepositoryTest extends MySqlIntegrationTestSupport {

    private static final long SELLER_ID = 987_600L;

    @Autowired
    private ListingRepository listingRepository;

    @Autowired
    private ListingImageRepository imageRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void insertSeller() {
        jdbcTemplate.update("""
                INSERT INTO members (id, login_id, password, role, phone_number, address)
                VALUES (?, 'thumbnail-test', 'encoded-password', 'MEMBER', '010-0000-0000', '가상 주소')
                """, SELLER_ID);
    }

    @Test
    void 요청한_매물의_순서_1_사진만_반환하고_전체_사진은_유지한다() {
        long fiveImages = createListing("사진 다섯 개");
        for (int order : List.of(5, 3, 1, 4, 2)) {
            imageRepository.save(ListingImage.create(fiveImages, "five-" + order + ".jpg", order));
        }
        long singleImage = createListing("사진 한 개");
        imageRepository.save(ListingImage.create(singleImage, "single-1.jpg", 1));
        long gapInOrder = createListing("순서 1이 없는 비정상 데이터");
        imageRepository.save(ListingImage.create(gapInOrder, "gap-5.jpg", 5));
        imageRepository.save(ListingImage.create(gapInOrder, "gap-3.jpg", 3));
        long noImage = createListing("사진 없음");
        long notRequested = createListing("조회 대상 아님");
        imageRepository.save(ListingImage.create(notRequested, "excluded.jpg", 1));
        imageRepository.flush();

        List<ListingImage> thumbnails = imageRepository.findThumbnailsByListingIdIn(
                List.of(fiveImages, singleImage, gapInOrder, noImage));

        assertThat(thumbnails)
                .extracting(ListingImage::getListingId, ListingImage::getObjectKey, ListingImage::getDisplayOrder)
                .containsExactlyInAnyOrder(
                        tuple(fiveImages, "five-1.jpg", 1),
                        tuple(singleImage, "single-1.jpg", 1));
        assertThat(imageRepository.findAllByListingIdOrderByDisplayOrderAsc(fiveImages))
                .extracting(ListingImage::getDisplayOrder)
                .containsExactly(1, 2, 3, 4, 5);
    }

    private long createListing(String title) {
        return listingRepository.saveAndFlush(Listing.create(
                SELLER_ID, "1111053000", null, title, "대표 사진 조회 테스트", 100_000,
                ListingCategory.DESK, ConditionGrade.A, Dimensions.of(100, 50, 40))).getId();
    }
}
