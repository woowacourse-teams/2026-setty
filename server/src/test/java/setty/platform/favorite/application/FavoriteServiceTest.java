package setty.platform.favorite.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.platform.listing.application.ListingView;
import setty.platform.listing.storage.ListingImageStorage;

@SpringBootTest
@Testcontainers
class FavoriteServiceTest {

    private static final long SELLER_ID = 101L;
    private static final long MEMBER_ID = 202L;
    private static final long FIRST_LISTING_ID = 11L;
    private static final long SECOND_LISTING_ID = 12L;

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.6")
            .withDatabaseName("setty_test")
            .withUsername("setty_test")
            .withPassword("setty_test");

    @Autowired
    private FavoriteService favoriteService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ListingImageStorage listingImageStorage;

    @BeforeEach
    void setUp() {
        cleanUp();
        insertMember(SELLER_ID);
        insertMember(MEMBER_ID);
        insertListing(FIRST_LISTING_ID, SELLER_ID);
        insertListing(SECOND_LISTING_ID, SELLER_ID);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM favorites");
        jdbcTemplate.update("DELETE FROM delivery");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM listing_images");
        jdbcTemplate.update("DELETE FROM listings");
        jdbcTemplate.update("DELETE FROM members");
    }

    @Test
    void 같은_매물을_두_번_찜해도_한_건만_저장되고_예외가_없다() {
        favoriteService.add(MEMBER_ID, FIRST_LISTING_ID);

        assertThatCode(() -> favoriteService.add(MEMBER_ID, FIRST_LISTING_ID))
                .doesNotThrowAnyException();
        assertThat(favoriteCount()).isEqualTo(1);
    }

    @Test
    void 찜하지_않은_매물을_해제해도_예외가_없다() {
        assertThatCode(() -> favoriteService.remove(MEMBER_ID, FIRST_LISTING_ID))
                .doesNotThrowAnyException();
    }

    @Test
    void 없는_매물을_찜하면_거부된다() {
        assertThatThrownBy(() -> favoriteService.add(MEMBER_ID, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.LISTING_NOT_FOUND);
    }

    @Test
    void 찜한_매물이_삭제되면_목록에서_제외된다() {
        favoriteService.add(MEMBER_ID, FIRST_LISTING_ID);
        favoriteService.add(MEMBER_ID, SECOND_LISTING_ID);
        jdbcTemplate.update("UPDATE listings SET deleted_at = NOW(6) WHERE id = ?", FIRST_LISTING_ID);

        List<ListingView.Summary> summaries = favoriteService.findMine(MEMBER_ID);

        assertThat(summaries)
                .extracting(ListingView.Summary::id)
                .containsExactly(SECOND_LISTING_ID);
    }

    @Test
    void 나중에_찜한_매물이_먼저_나온다() {
        favoriteService.add(MEMBER_ID, FIRST_LISTING_ID);
        favoriteService.add(MEMBER_ID, SECOND_LISTING_ID);

        List<ListingView.Summary> summaries = favoriteService.findMine(MEMBER_ID);

        assertThat(summaries)
                .extracting(ListingView.Summary::id)
                .containsExactly(SECOND_LISTING_ID, FIRST_LISTING_ID);
    }

    @Test
    void 찜_여부를_조회한다() {
        favoriteService.add(MEMBER_ID, FIRST_LISTING_ID);

        assertThat(favoriteService.isFavorited(MEMBER_ID, FIRST_LISTING_ID)).isTrue();
        assertThat(favoriteService.isFavorited(MEMBER_ID, SECOND_LISTING_ID)).isFalse();
    }

    private int favoriteCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM favorites", Integer.class);
    }

    private void insertMember(final long memberId) {
        jdbcTemplate.update(
                """
                INSERT INTO members (id, login_id, password, role, phone_number, address, token)
                VALUES (?, ?, 'encoded-password', 'MEMBER', '010-0000-0000', '가상 주소', ?)
                """,
                memberId,
                "member" + memberId,
                "token-" + memberId
        );
    }

    private void insertListing(final long listingId, final long sellerId) {
        jdbcTemplate.update(
                """
                INSERT INTO listings (id, seller_id, title, description, price, delivery_fee, category,
                                      condition_grade, width_cm, depth_cm, height_cm, sale_status,
                                      has_purchase_request, created_at, updated_at, deleted_at)
                VALUES (?, ?, '테스트 책상', '테스트 설명', 150000, 10000, 'DESK',
                        'A', 60, 60, 70, 'AVAILABLE', false, NOW(6), NOW(6), NULL)
                """,
                listingId,
                sellerId
        );
    }
}
