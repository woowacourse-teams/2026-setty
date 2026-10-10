package setty.notification.listing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static setty.global.event.EventPublicationTestSupport.awaitEventsHandled;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import setty.notification.keyword.application.KeywordSubscriptionService;
import setty.platform.listing.application.ListingCreateCommand;
import setty.platform.listing.application.ListingService;
import setty.platform.listing.application.ListingUpdateCommand;
import setty.platform.listing.domain.ConditionGrade;
import setty.platform.listing.domain.Dimensions;
import setty.platform.listing.domain.ListingCategory;
import setty.platform.listing.storage.ListingImageStorage;
import setty.support.MySqlIntegrationTestSupport;

@SpringBootTest
class ListingNotificationIntegrationTest extends MySqlIntegrationTestSupport {

    private static final long SELLER_ID = 901L;
    private static final long FIRST_SUBSCRIBER_ID = 902L;
    private static final long SECOND_SUBSCRIBER_ID = 903L;
    private static final long THIRD_SUBSCRIBER_ID = 904L;

    @Autowired
    private ListingService listingService;

    @Autowired
    private KeywordSubscriptionService keywordSubscriptionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ListingImageStorage listingImageStorage;

    @BeforeEach
    void setUp() {
        cleanUp();
        for (final long memberId : List.of(SELLER_ID, FIRST_SUBSCRIBER_ID, SECOND_SUBSCRIBER_ID, THIRD_SUBSCRIBER_ID)) {
            insertMember(memberId);
        }
        when(listingImageStorage.upload(anyList())).thenReturn(List.of("listings/test.jpg"));
    }

    @AfterEach
    void cleanUp() {
        awaitEventsHandled(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM EVENT_PUBLICATION");
        jdbcTemplate.update("DELETE FROM listing_notifications");
        jdbcTemplate.update("DELETE FROM keyword_subscriptions");
        jdbcTemplate.update("DELETE FROM listing_images");
        jdbcTemplate.update("DELETE FROM listings");
        jdbcTemplate.update("DELETE FROM members");
    }

    @Test
    void 제목에_키워드가_있으면_구독자_전원에게_알림이_생성된다() {
        subscribe(FIRST_SUBSCRIBER_ID, "아이폰");
        subscribe(SECOND_SUBSCRIBER_ID, "아이폰");
        subscribe(THIRD_SUBSCRIBER_ID, "아이폰");

        final long listingId = createListing("아이폰 15 팝니다");

        assertThat(notificationCount(listingId)).isEqualTo(3);
        assertThat(countByStatus(listingId, "PENDING")).isEqualTo(3);
    }

    @Test
    void 제목에_키워드가_없으면_알림이_없다() {
        subscribe(FIRST_SUBSCRIBER_ID, "아이폰");

        final long listingId = createListing("갤럭시 S24 팝니다");

        assertThat(notificationCount(listingId)).isZero();
    }

    @Test
    void 매물을_수정해도_알림이_추가되지_않는다() {
        subscribe(FIRST_SUBSCRIBER_ID, "아이폰");
        final long listingId = createListing("아이폰 15 팝니다");

        listingService.update(SELLER_ID, listingId, new ListingUpdateCommand(
                "아이폰 15 프로 팝니다", "수정된 설명", 900_000, ListingCategory.DESK, ConditionGrade.A,
                Dimensions.of(10, 10, 10), retainedImageIds(listingId), List.of()));

        assertThat(notificationCount(listingId)).isEqualTo(1);
    }

    @Test
    void 한_사용자가_여러_키워드에_걸려도_알림은_매물당_1건이다() {
        subscribe(FIRST_SUBSCRIBER_ID, "아이폰");
        subscribe(FIRST_SUBSCRIBER_ID, "15");

        final long listingId = createListing("아이폰 15 팝니다");

        assertThat(notificationCount(listingId)).isEqualTo(1);
    }

    @Test
    void 판매자_본인은_자기_매물_알림을_받지_않는다() {
        subscribe(SELLER_ID, "아이폰");
        subscribe(FIRST_SUBSCRIBER_ID, "아이폰");

        final long listingId = createListing("아이폰 15 팝니다");

        assertThat(notificationCount(listingId)).isEqualTo(1);
        assertThat(countByMember(listingId, SELLER_ID)).isZero();
    }

    @Test
    void 대소문자와_앞뒤_공백이_달라도_매칭된다() {
        subscribe(FIRST_SUBSCRIBER_ID, "  iphone ");

        final long listingId = createListing("IPHONE 15 팝니다");

        assertThat(notificationCount(listingId)).isEqualTo(1);
    }

    private void subscribe(final long memberId, final String keyword) {
        keywordSubscriptionService.subscribe(memberId, keyword);
    }

    private long createListing(final String title) {
        return listingService.create(SELLER_ID, new ListingCreateCommand(
                title, "테스트 설명", 1_000_000, ListingCategory.DESK, ConditionGrade.A, Dimensions.of(10, 10, 10),
                List.of(new MockMultipartFile("images", "test.jpg", "image/jpeg", new byte[]{1}))
        )).listingId();
    }

    private List<Long> retainedImageIds(final long listingId) {
        return jdbcTemplate.queryForList("SELECT id FROM listing_images WHERE listing_id = ?", Long.class, listingId);
    }

    private long notificationCount(final long listingId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM listing_notifications WHERE listing_id = ?", Long.class, listingId);
    }

    private long countByStatus(final long listingId, final String status) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM listing_notifications WHERE listing_id = ? AND status = ?",
                Long.class, listingId, status);
    }

    private long countByMember(final long listingId, final long memberId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM listing_notifications WHERE listing_id = ? AND member_id = ?",
                Long.class, listingId, memberId);
    }

    private void insertMember(final long memberId) {
        jdbcTemplate.update(
                """
                INSERT INTO members (id, login_id, password, role, phone_number, address, token)
                VALUES (?, ?, 'encoded-password', 'MEMBER', '010-0000-0000', '가상 주소', ?)
                """,
                memberId, "member" + memberId, "token-" + memberId);
    }
}
