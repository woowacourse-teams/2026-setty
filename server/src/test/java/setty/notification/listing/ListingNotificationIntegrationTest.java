package setty.notification.listing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static setty.global.event.EventPublicationTestSupport.awaitEventsHandled;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.Awaitility;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import setty.notification.keyword.application.KeywordSubscriptionService;
import setty.notification.listing.application.ListingNotificationDispatcher;
import setty.notification.listing.application.ListingNotificationService;
import setty.notification.listing.application.ListingToNotify;
import setty.notification.send.NotificationSender;
import setty.notification.send.SendResult;
import setty.deliveryfee.DeliveryFeeService;
import setty.deliveryfee.Dong;
import setty.deliveryfee.VolumeTier;
import setty.notification.keyword.domain.KeywordConditions;
import setty.notification.send.NotificationMessage;
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
    private static final String SELLER_DONG_CODE = "1111053000";

    @Autowired
    private ListingService listingService;

    @Autowired
    private KeywordSubscriptionService keywordSubscriptionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ListingNotificationDispatcher listingNotificationDispatcher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ListingNotificationService listingNotificationService;

    @Autowired
    private DeliveryFeeService deliveryFeeService;

    @MockitoBean
    private ListingImageStorage listingImageStorage;

    @MockitoBean
    private NotificationSender notificationSender;

    @BeforeEach
    void setUp() {
        cleanUp();
        for (final long memberId : List.of(SELLER_ID, FIRST_SUBSCRIBER_ID, SECOND_SUBSCRIBER_ID, THIRD_SUBSCRIBER_ID)) {
            insertMember(memberId);
        }
        when(listingImageStorage.upload(anyList())).thenReturn(List.of("listings/test.jpg"));
        when(notificationSender.send(anyList())).thenReturn(SendResult.allSucceeded());
    }

    @AfterEach
    void cleanUp() {
        awaitEventsHandled(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM EVENT_PUBLICATION");
        jdbcTemplate.update("DELETE FROM listing_notifications");
        jdbcTemplate.update("DELETE FROM keyword_exclusions");
        jdbcTemplate.update("DELETE FROM keyword_subscriptions");
        jdbcTemplate.update("DELETE FROM listing_images");
        jdbcTemplate.update("DELETE FROM listings");
        jdbcTemplate.update("DELETE FROM members");
    }

    @Test
    void 제목에_키워드가_있으면_구독자_전원에게_알림이_생성되고_정확히_1회_발송된다() {
        subscribe(FIRST_SUBSCRIBER_ID, "아이폰");
        subscribe(SECOND_SUBSCRIBER_ID, "아이폰");
        subscribe(THIRD_SUBSCRIBER_ID, "아이폰");

        final long listingId = createListing("아이폰 15 팝니다");
        awaitEventsHandled(jdbcTemplate);

        assertThat(notificationCount(listingId)).isEqualTo(3);
        assertThat(countByStatus(listingId, "SENT")).isEqualTo(3);
        assertThat(unsentCount(listingId)).isZero();
        assertThat(maxAttempts(listingId)).isEqualTo(1);
    }

    @Test
    void 등록_응답은_발송_지연을_기다리지_않는다() {
        for (long memberId = 1_000L; memberId < 1_010L; memberId++) {
            insertMember(memberId);
            subscribe(memberId, "아이폰");
        }
        when(notificationSender.send(anyList())).thenAnswer(invocation -> {
            Thread.sleep(500);
            return SendResult.allSucceeded();
        });

        final long started = System.nanoTime();
        final long listingId = createListing("아이폰 15 팝니다");
        final long elapsedMs = (System.nanoTime() - started) / 1_000_000;

        assertThat(elapsedMs).isLessThan(500);
        Awaitility.await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(countByStatus(listingId, "SENT")).isEqualTo(10));
    }

    @Test
    void 발송이_한_번_실패하면_PENDING으로_남고_시도_횟수가_기록된다() {
        subscribe(FIRST_SUBSCRIBER_ID, "아이폰");
        final AtomicInteger calls = new AtomicInteger();
        when(notificationSender.send(anyList())).thenAnswer(invocation -> calls.incrementAndGet() == 1
                ? SendResult.allFailed(invocation.getArgument(0))
                : SendResult.allSucceeded());

        final long listingId = createListing("아이폰 15 팝니다");
        awaitEventsHandled(jdbcTemplate);

        final Map<String, Object> afterFirst = notification(listingId);
        assertThat(afterFirst.get("status")).isEqualTo("PENDING");
        assertThat(afterFirst.get("attempts")).isEqualTo(1);

        dispatchQuietly(listingId);

        final Map<String, Object> afterSecond = notification(listingId);
        assertThat(afterSecond.get("status")).isEqualTo("SENT");
        assertThat(afterSecond.get("attempts")).isEqualTo(2);
    }

    @Test
    void 트랜잭션_안에서_전부_보내면_남은_건수를_0으로_보고한다() {
        for (final long memberId : List.of(FIRST_SUBSCRIBER_ID, SECOND_SUBSCRIBER_ID, THIRD_SUBSCRIBER_ID)) {
            subscribe(memberId, "아이폰");
        }
        final long listingId = createListing("아이폰 15 팝니다");
        awaitEventsHandled(jdbcTemplate);
        jdbcTemplate.update("UPDATE listing_notifications SET status = 'PENDING', sent_at = NULL WHERE listing_id = ?",
                listingId);

        assertThatCode(() -> transactionTemplate.executeWithoutResult(
                status -> listingNotificationDispatcher.dispatch(listing(listingId))))
                .doesNotThrowAnyException();
        assertThat(countByStatus(listingId, "SENT")).isEqualTo(3);
    }

    @Test
    void 실패해_남은_건이_있으면_재발행을_위해_예외를_던진다() {
        subscribe(FIRST_SUBSCRIBER_ID, "아이폰");
        when(notificationSender.send(anyList()))
                .thenAnswer(invocation -> SendResult.allFailed(invocation.getArgument(0)));
        final long listingId = createListing("아이폰 15 팝니다");
        awaitEventsHandled(jdbcTemplate);

        assertThatThrownBy(() -> listingNotificationDispatcher.dispatch(listing(listingId)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1 notifications left");
    }

    @Test
    void 세_번_연속_실패하면_FAILED로_굳고_더_시도하지_않는다() {
        subscribe(FIRST_SUBSCRIBER_ID, "아이폰");
        when(notificationSender.send(anyList()))
                .thenAnswer(invocation -> SendResult.allFailed(invocation.getArgument(0)));

        final long listingId = createListing("아이폰 15 팝니다");
        awaitEventsHandled(jdbcTemplate);
        dispatchQuietly(listingId);
        dispatchQuietly(listingId);
        dispatchQuietly(listingId);

        final Map<String, Object> result = notification(listingId);
        assertThat(result.get("status")).isEqualTo("FAILED");
        assertThat(result.get("attempts")).isEqualTo(3);
    }

    @Test
    void 제목에_키워드가_없으면_알림이_없다() {
        subscribe(FIRST_SUBSCRIBER_ID, "아이폰");

        final long listingId = createListing("갤럭시 S24 팝니다");
        awaitEventsHandled(jdbcTemplate);

        assertThat(notificationCount(listingId)).isZero();
    }

    @Test
    void 알림_생성이_다시_실행돼도_중복되지_않는다() {
        subscribe(FIRST_SUBSCRIBER_ID, "아이폰");
        subscribe(SECOND_SUBSCRIBER_ID, "아이폰");
        final long listingId = createListing("아이폰 15 팝니다");
        awaitEventsHandled(jdbcTemplate);

        assertThatCode(() -> listingNotificationService.createForListing(listing(listingId)))
                .doesNotThrowAnyException();
        assertThat(notificationCount(listingId)).isEqualTo(2);
        assertThat(countByStatus(listingId, "SENT")).isEqualTo(2);
    }

    @Test
    void 매물을_수정해도_알림이_추가되지_않는다() {
        subscribe(FIRST_SUBSCRIBER_ID, "아이폰");
        final long listingId = createListing("아이폰 15 팝니다");
        awaitEventsHandled(jdbcTemplate);

        listingService.update(SELLER_ID, listingId, new ListingUpdateCommand(
                "아이폰 15 프로 팝니다", "수정된 설명", 900_000, ListingCategory.DESK, ConditionGrade.A,
                Dimensions.of(10, 10, 10), retainedImageIds(listingId), List.of()));
        awaitEventsHandled(jdbcTemplate);

        assertThat(notificationCount(listingId)).isEqualTo(1);
    }

    @Test
    void 한_사용자가_여러_키워드에_걸려도_알림은_매물당_1건이다() {
        subscribe(FIRST_SUBSCRIBER_ID, "아이폰");
        subscribe(FIRST_SUBSCRIBER_ID, "15");

        final long listingId = createListing("아이폰 15 팝니다");
        awaitEventsHandled(jdbcTemplate);

        assertThat(notificationCount(listingId)).isEqualTo(1);
    }

    @Test
    void 판매자_본인은_자기_매물_알림을_받지_않는다() {
        subscribe(SELLER_ID, "아이폰");
        subscribe(FIRST_SUBSCRIBER_ID, "아이폰");

        final long listingId = createListing("아이폰 15 팝니다");
        awaitEventsHandled(jdbcTemplate);

        assertThat(notificationCount(listingId)).isEqualTo(1);
        assertThat(countByMember(listingId, SELLER_ID)).isZero();
    }

    @Test
    void 대소문자와_앞뒤_공백이_달라도_매칭된다() {
        subscribe(FIRST_SUBSCRIBER_ID, "  iphone ");

        final long listingId = createListing("IPHONE 15 팝니다");
        awaitEventsHandled(jdbcTemplate);

        assertThat(notificationCount(listingId)).isEqualTo(1);
    }

    @Test
    void 카테고리_조건이_다르면_받지_않고_같으면_받는다() {
        subscribeWith(FIRST_SUBSCRIBER_ID, "아이폰", KeywordConditions.of("CHAIR", null, null, null, null, null));
        subscribeWith(SECOND_SUBSCRIBER_ID, "아이폰", KeywordConditions.of("DESK", null, null, null, null, null));

        final long listingId = createListing("아이폰 15 팝니다");
        awaitEventsHandled(jdbcTemplate);

        assertThat(countByMember(listingId, FIRST_SUBSCRIBER_ID)).isZero();
        assertThat(countByMember(listingId, SECOND_SUBSCRIBER_ID)).isEqualTo(1);
    }

    @Test
    void 물품가_범위는_경계값을_포함한다() {
        subscribeWith(FIRST_SUBSCRIBER_ID, "아이폰", KeywordConditions.of(null, 1_000_000, 1_000_000, null, null, null));
        subscribeWith(SECOND_SUBSCRIBER_ID, "아이폰", KeywordConditions.of(null, 1_000_001, null, null, null, null));
        subscribeWith(THIRD_SUBSCRIBER_ID, "아이폰", KeywordConditions.of(null, null, 999_999, null, null, null));

        final long listingId = createListing("아이폰 15 팝니다");
        awaitEventsHandled(jdbcTemplate);

        assertThat(countByMember(listingId, FIRST_SUBSCRIBER_ID)).isEqualTo(1);
        assertThat(countByMember(listingId, SECOND_SUBSCRIBER_ID)).isZero();
        assertThat(countByMember(listingId, THIRD_SUBSCRIBER_ID)).isZero();
    }

    @Test
    void 제외_키워드가_하나라도_제목에_있으면_받지_않는다() {
        subscribeWith(FIRST_SUBSCRIBER_ID, "아이폰", KeywordConditions.of(null, null, null, null, null, List.of("팝니다")));
        subscribeWith(SECOND_SUBSCRIBER_ID, "아이폰", KeywordConditions.of(null, null, null, null, null, List.of("삽니다")));
        subscribeWith(THIRD_SUBSCRIBER_ID, "아이폰", KeywordConditions.of(null, null, null, null, null, List.of("삽니다", "중고", "15")));

        final long listingId = createListing("아이폰 15 팝니다");
        awaitEventsHandled(jdbcTemplate);

        assertThat(countByMember(listingId, FIRST_SUBSCRIBER_ID)).isZero();
        assertThat(countByMember(listingId, SECOND_SUBSCRIBER_ID)).isEqualTo(1);
        assertThat(countByMember(listingId, THIRD_SUBSCRIBER_ID)).isZero();
    }

    @Test
    void 배송비_상한은_구독자_위치로_계산한_배송비로_판정한다() {
        final int nearFee = deliveryFeeService.fee(sellerDong().index(), sellerDong().index(), VolumeTier.SMALL);
        final Dong farDong = farDong(nearFee);
        jdbcTemplate.update("UPDATE members SET dong_code = ? WHERE id = ?", SELLER_DONG_CODE, FIRST_SUBSCRIBER_ID);
        jdbcTemplate.update("UPDATE members SET dong_code = ? WHERE id = ?", farDong.code(), SECOND_SUBSCRIBER_ID);
        subscribeWith(FIRST_SUBSCRIBER_ID, "아이폰", KeywordConditions.of(null, null, null, nearFee, null, null));
        subscribeWith(SECOND_SUBSCRIBER_ID, "아이폰", KeywordConditions.of(null, null, null, nearFee, null, null));

        final long listingId = createListing("아이폰 15 팝니다");
        awaitEventsHandled(jdbcTemplate);

        assertThat(countByMember(listingId, FIRST_SUBSCRIBER_ID)).isEqualTo(1);
        assertThat(countByMember(listingId, SECOND_SUBSCRIBER_ID)).isZero();
    }

    @Test
    void 동을_모르는_구독자는_부피_기준_배송비로_판정한다() {
        subscribeWith(FIRST_SUBSCRIBER_ID, "아이폰", KeywordConditions.of(null, null, null, 10_000, null, null));
        subscribeWith(SECOND_SUBSCRIBER_ID, "아이폰", KeywordConditions.of(null, null, null, 9_999, null, null));

        final long listingId = createListing("아이폰 15 팝니다");
        awaitEventsHandled(jdbcTemplate);

        assertThat(countByMember(listingId, FIRST_SUBSCRIBER_ID)).isEqualTo(1);
        assertThat(countByMember(listingId, SECOND_SUBSCRIBER_ID)).isZero();
    }

    @Test
    void 총가격_상한은_물품가와_배송비를_더해_판정한다() {
        subscribeWith(FIRST_SUBSCRIBER_ID, "아이폰", KeywordConditions.of(null, null, null, null, 1_010_000, null));
        subscribeWith(SECOND_SUBSCRIBER_ID, "아이폰", KeywordConditions.of(null, null, null, null, 1_009_999, null));

        final long listingId = createListing("아이폰 15 팝니다");
        awaitEventsHandled(jdbcTemplate);

        assertThat(countByMember(listingId, FIRST_SUBSCRIBER_ID)).isEqualTo(1);
        assertThat(countByMember(listingId, SECOND_SUBSCRIBER_ID)).isZero();
    }

    @Test
    void 조건_여럿이_전부_맞아야_받는다() {
        subscribeWith(FIRST_SUBSCRIBER_ID, "아이폰",
                KeywordConditions.of("DESK", 900_000, 1_100_000, 10_000, 1_010_000, List.of("중고")));
        subscribeWith(SECOND_SUBSCRIBER_ID, "아이폰",
                KeywordConditions.of("DESK", 900_000, 1_100_000, 10_000, 1_010_000, List.of("팝니다")));

        final long listingId = createListing("아이폰 15 팝니다");
        awaitEventsHandled(jdbcTemplate);

        assertThat(countByMember(listingId, FIRST_SUBSCRIBER_ID)).isEqualTo(1);
        assertThat(countByMember(listingId, SECOND_SUBSCRIBER_ID)).isZero();
    }

    @Test
    void 조건_없는_키워드와_배송비_조건_키워드에_같이_걸려도_알림은_1건이다() {
        subscribe(FIRST_SUBSCRIBER_ID, "아이폰");
        subscribeWith(FIRST_SUBSCRIBER_ID, "15", KeywordConditions.of(null, null, null, 10_000, null, null));

        final long listingId = createListing("아이폰 15 팝니다");
        awaitEventsHandled(jdbcTemplate);

        assertThat(countByMember(listingId, FIRST_SUBSCRIBER_ID)).isEqualTo(1);
        assertThat(countByStatus(listingId, "SENT")).isEqualTo(1);
    }

    @Test
    void 발송_메시지에_물품가와_받는_사람_기준_배송비를_싣는다() {
        subscribe(FIRST_SUBSCRIBER_ID, "아이폰");

        final long listingId = createListing("아이폰 15 팝니다");
        awaitEventsHandled(jdbcTemplate);

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<List<NotificationMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationSender).send(captor.capture());
        final NotificationMessage message = captor.getValue().getFirst();
        assertThat(message.listingId()).isEqualTo(listingId);
        assertThat(message.price()).isEqualTo(1_000_000);
        assertThat(message.deliveryFee()).isEqualTo(10_000);
    }

    private void dispatchQuietly(final long listingId) {
        try {
            listingNotificationDispatcher.dispatch(listing(listingId));
        } catch (final IllegalStateException leftToRetry) {
        }
    }

    private ListingToNotify listing(final long listingId) {
        return new ListingToNotify(listingId, SELLER_ID, "아이폰 15 팝니다", "DESK", 1_000_000, "1111053000", null,
                VolumeTier.SMALL, 10_000);
    }

    private void subscribe(final long memberId, final String keyword) {
        keywordSubscriptionService.subscribe(memberId, keyword);
    }

    private void subscribeWith(final long memberId, final String keyword, final KeywordConditions conditions) {
        keywordSubscriptionService.subscribe(memberId, keyword);
        final Long subscriptionId = jdbcTemplate.queryForObject(
                "SELECT id FROM keyword_subscriptions WHERE member_id = ? AND keyword = ?", Long.class, memberId, keyword);
        keywordSubscriptionService.updateConditions(memberId, subscriptionId, conditions);
    }

    private Dong sellerDong() {
        return deliveryFeeService.findDong(SELLER_DONG_CODE).orElseThrow();
    }

    private Dong farDong(final int nearFee) {
        return deliveryFeeService.dongs().stream()
                .filter(dong -> deliveryFeeService.fee(sellerDong().index(), dong.index(), VolumeTier.SMALL) > nearFee)
                .findFirst()
                .orElseThrow();
    }

    private long createListing(final String title) {
        return listingService.create(SELLER_ID, SELLER_DONG_CODE, null, new ListingCreateCommand(
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

    private long unsentCount(final long listingId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM listing_notifications WHERE listing_id = ? AND sent_at IS NULL",
                Long.class, listingId);
    }

    private int maxAttempts(final long listingId) {
        return jdbcTemplate.queryForObject(
                "SELECT MAX(attempts) FROM listing_notifications WHERE listing_id = ?", Integer.class, listingId);
    }

    private Map<String, Object> notification(final long listingId) {
        return jdbcTemplate.queryForMap(
                "SELECT status, attempts FROM listing_notifications WHERE listing_id = ?", listingId);
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
