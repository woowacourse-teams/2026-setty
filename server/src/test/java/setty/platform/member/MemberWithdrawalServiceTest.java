package setty.platform.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static setty.global.event.EventPublicationTestSupport.awaitEventsHandled;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.Map;
import java.util.ArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import setty.address.AddressResolution.Status;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.platform.listing.storage.ListingImageStorage;
import setty.platform.member.controller.dto.LoginRequest;
import setty.platform.member.controller.dto.SignupRequest;
import setty.platform.member.domain.Member;
import setty.platform.member.repository.MemberRepository;
import setty.platform.member.service.AuthService;
import setty.platform.member.service.MemberWithdrawalService;
import setty.platform.member.controller.dto.UpdateDongRequest;
import setty.platform.member.controller.dto.UpdateProfileRequest;
import setty.support.MySqlIntegrationTestSupport;

@SpringBootTest
class MemberWithdrawalServiceTest extends MySqlIntegrationTestSupport {

    private static final long MEMBER_ID = 101L;
    private static final long OTHER_MEMBER_ID = 202L;
    private static final long ON_SALE_LISTING_ID = 11L;
    private static final long SOLD_LISTING_ID = 12L;
    private static final long OTHER_LISTING_ID = 21L;

    @Autowired
    private MemberWithdrawalService memberWithdrawalService;

    @Autowired
    private AuthService authService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ListingImageStorage listingImageStorage;

    @BeforeEach
    void setUp() {
        cleanUp();
        insertMember(MEMBER_ID);
        insertMember(OTHER_MEMBER_ID);
        insertListing(ON_SALE_LISTING_ID, MEMBER_ID, "AVAILABLE");
        insertListing(SOLD_LISTING_ID, MEMBER_ID, "SOLD");
        insertListing(OTHER_LISTING_ID, OTHER_MEMBER_ID, "AVAILABLE");
        jdbcTemplate.update("INSERT INTO listing_images (listing_id, object_key, display_order) VALUES (?, 'on-sale.jpg', 0)",
                ON_SALE_LISTING_ID);
        insertOrder(1L, SOLD_LISTING_ID, OTHER_MEMBER_ID, "COMPLETED");
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM EVENT_PUBLICATION");
        jdbcTemplate.update("DELETE FROM listing_notifications");
        jdbcTemplate.update("DELETE FROM keyword_subscriptions");
        jdbcTemplate.update("DELETE FROM device_tokens");
        jdbcTemplate.update("DELETE FROM favorites");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM listing_images");
        jdbcTemplate.update("DELETE FROM listings");
        jdbcTemplate.update("DELETE FROM members");
    }

    @Test
    void 탈퇴하면_행은_남기고_개인정보와_토큰을_가린다() {
        memberWithdrawalService.withdraw(MEMBER_ID);

        final Member withdrawn = memberRepository.findById(MEMBER_ID).orElseThrow();
        assertThat(withdrawn.getLoginId()).isEqualTo("withdrawn-101");
        assertThat(withdrawn.getPassword()).isNotEqualTo("encoded-password").hasSize(60);
        assertThat(withdrawn.getPhoneNumber()).isEqualTo("000-0000-0000");
        assertThat(withdrawn.getAddress()).isEqualTo("탈퇴한 회원");
        assertThat(withdrawn.getDongCode()).isNull();
        assertThat(withdrawn.getZipCode()).isNull();
        assertThat(withdrawn.getResolvedAddress().getStatus()).isEqualTo(Status.UNMATCHED);
        assertThat(withdrawn.getToken()).isNull();
        assertThat(withdrawn.isWithdrawn()).isTrue();
    }

    @Test
    void 탈퇴하면_판매_중_매물은_내리고_사진을_지우며_판매_완료_매물은_남긴다() {
        memberWithdrawalService.withdraw(MEMBER_ID);

        assertThat(isDeleted(ON_SALE_LISTING_ID)).isTrue();
        assertThat(isDeleted(SOLD_LISTING_ID)).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT order_status FROM orders WHERE id = 1", String.class))
                .isEqualTo("COMPLETED");
        verify(listingImageStorage).deleteAll(List.of("on-sale.jpg"));
    }

    @Test
    void 탈퇴하면_찜_키워드_구독_기기_토큰_알림을_지운다() {
        jdbcTemplate.update("INSERT INTO favorites (member_id, listing_id, created_at) VALUES (?, ?, NOW(6))",
                MEMBER_ID, OTHER_LISTING_ID);
        jdbcTemplate.update("INSERT INTO favorites (member_id, listing_id, created_at) VALUES (?, ?, NOW(6))",
                OTHER_MEMBER_ID, ON_SALE_LISTING_ID);
        jdbcTemplate.update("INSERT INTO keyword_subscriptions (member_id, keyword, created_at) VALUES (?, '의자', NOW(6))",
                MEMBER_ID);
        jdbcTemplate.update("""
                INSERT INTO keyword_exclusions (subscription_id, word)
                SELECT id, '사무용' FROM keyword_subscriptions WHERE member_id = ?
                """, MEMBER_ID);
        jdbcTemplate.update("""
                INSERT INTO device_tokens (member_id, token, platform, updated_at)
                VALUES (?, 'device-101', 'WEB', NOW(6))
                """, MEMBER_ID);
        jdbcTemplate.update("""
                INSERT INTO listing_notifications (listing_id, member_id, status, attempts, created_at)
                VALUES (?, ?, 'SENT', 1, NOW(6))
                """, OTHER_LISTING_ID, MEMBER_ID);

        memberWithdrawalService.withdraw(MEMBER_ID);
        awaitEventsHandled(jdbcTemplate);

        assertThat(countOf("favorites", MEMBER_ID)).isZero();
        assertThat(countOf("keyword_subscriptions", MEMBER_ID)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM keyword_exclusions", Integer.class)).isZero();
        assertThat(countOf("device_tokens", MEMBER_ID)).isZero();
        assertThat(countOf("listing_notifications", MEMBER_ID)).isZero();
        assertThat(countOf("favorites", OTHER_MEMBER_ID)).isOne();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "CONFIRMED", "CANCEL_PENDING"})
    void 구매자로_진행_중인_주문이_있으면_탈퇴를_막는다(final String orderStatus) {
        insertOrder(2L, OTHER_LISTING_ID, MEMBER_ID, orderStatus);

        assertActiveTradeRejected();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "CONFIRMED", "CANCEL_PENDING"})
    void 판매자로_내_매물에_진행_중인_주문이_있으면_탈퇴를_막는다(final String orderStatus) {
        jdbcTemplate.update("UPDATE listings SET has_purchase_request = true WHERE id = ?", ON_SALE_LISTING_ID);
        insertOrder(2L, ON_SALE_LISTING_ID, OTHER_MEMBER_ID, orderStatus);

        assertActiveTradeRejected();
    }

    @ParameterizedTest
    @ValueSource(strings = {"COMPLETED", "CANCELLED", "EXPIRED"})
    void 끝난_주문만_있으면_탈퇴할_수_있다(final String orderStatus) {
        insertOrder(2L, OTHER_LISTING_ID, MEMBER_ID, orderStatus);

        memberWithdrawalService.withdraw(MEMBER_ID);

        assertThat(memberRepository.findById(MEMBER_ID).orElseThrow().isWithdrawn()).isTrue();
    }

    @Test
    void 탈퇴한_아이디로는_로그인할_수_없고_같은_아이디로_다시_가입할_수_있다() {
        memberWithdrawalService.withdraw(MEMBER_ID);

        assertThatThrownBy(() -> authService.login(new LoginRequest("member101", "password123")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.LOGIN_FAILED);

        final Member rejoined = authService.signup(
                new SignupRequest("member101", "password123", "010-1234-5678", "가상 주소", null));
        assertThat(rejoined.getId()).isNotEqualTo(MEMBER_ID);
    }

    @Test
    void 이미_탈퇴했으면_다시_탈퇴하지_않는다() {
        memberWithdrawalService.withdraw(MEMBER_ID);
        final String maskedPassword = memberRepository.findById(MEMBER_ID).orElseThrow().getPassword();

        assertThatThrownBy(() -> memberWithdrawalService.withdraw(MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
        assertThat(memberRepository.findById(MEMBER_ID).orElseThrow().getPassword()).isEqualTo(maskedPassword);
    }

    @Test
    void 탈퇴한_회원은_정보_수정_동_변경_로그아웃을_할_수_없다() {
        memberWithdrawalService.withdraw(MEMBER_ID);

        for (final Runnable update : List.<Runnable>of(
                () -> authService.updateProfile(MEMBER_ID, new UpdateProfileRequest("010-1111-2222", "새 주소")),
                () -> authService.updateDong(MEMBER_ID, new UpdateDongRequest("1111053000")),
                () -> authService.logout(MEMBER_ID))) {
            assertThatThrownBy(update::run)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_TOKEN);
        }
        assertWithdrawnAndMasked();
    }

    @RepeatedTest(5)
    void 탈퇴와_로그인이_동시에_와도_탈퇴가_되살아나지_않는다() throws Exception {
        jdbcTemplate.update("UPDATE members SET password = ? WHERE id = ?",
                new BCryptPasswordEncoder().encode("password123"), MEMBER_ID);

        runTogether(
                () -> authService.login(new LoginRequest("member101", "password123")),
                () -> memberWithdrawalService.withdraw(MEMBER_ID)
        );

        assertWithdrawnAndMasked();
    }

    @RepeatedTest(5)
    void 탈퇴와_정보_수정이_동시에_와도_탈퇴가_되살아나지_않는다() throws Exception {
        runTogether(
                () -> authService.updateProfile(MEMBER_ID, new UpdateProfileRequest("010-1111-2222", "새 주소")),
                () -> memberWithdrawalService.withdraw(MEMBER_ID)
        );

        assertWithdrawnAndMasked();
    }

    private void assertWithdrawnAndMasked() {
        final Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT login_id, phone_number, token, withdrawn_at FROM members WHERE id = ?", MEMBER_ID);
        assertThat(row.get("withdrawn_at")).isNotNull();
        assertThat(row.get("login_id")).isEqualTo("withdrawn-101");
        assertThat(row.get("phone_number")).isEqualTo("000-0000-0000");
        assertThat(row.get("token")).isNull();
    }

    // 두 작업을 동시에 출발시키고 둘 다 끝날 때까지 기다린다. 어느 쪽이 실패하든 결과만 모은다.
    private void runTogether(final Runnable first, final Runnable second) throws Exception {
        final CountDownLatch start = new CountDownLatch(1);
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            final List<Future<?>> futures = new ArrayList<>();
            for (final Runnable task : List.of(first, second)) {
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        task.run();
                    } catch (final BusinessException ignored) {
                        // 순서에 따라 한쪽이 거절되는 것은 정상이다
                    }
                    return null;
                }));
            }
            start.countDown();
            for (final Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private void assertActiveTradeRejected() {
        assertThatThrownBy(() -> memberWithdrawalService.withdraw(MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_HAS_ACTIVE_TRADE);

        final Member member = memberRepository.findById(MEMBER_ID).orElseThrow();
        assertThat(member.isWithdrawn()).isFalse();
        assertThat(member.getToken()).isEqualTo("token-101");
        assertThat(isDeleted(ON_SALE_LISTING_ID)).isFalse();
    }

    private boolean isDeleted(final long listingId) {
        return jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM listings WHERE id = ?", Boolean.class, listingId);
    }

    private int countOf(final String table, final long memberId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE member_id = ?", Integer.class, memberId);
    }

    private void insertMember(final long memberId) {
        jdbcTemplate.update(
                """
                INSERT INTO members (id, login_id, password, role, phone_number, address, dong_code, token)
                VALUES (?, ?, 'encoded-password', 'MEMBER', '010-0000-0000', '가상 주소', '1111053000', ?)
                """,
                memberId,
                "member" + memberId,
                "token-" + memberId
        );
    }

    private void insertListing(final long listingId, final long sellerId, final String saleStatus) {
        jdbcTemplate.update(
                """
                INSERT INTO listings (id, seller_id, title, description, price, delivery_fee, category,
                                      condition_grade, width_cm, depth_cm, height_cm, sale_status,
                                      has_purchase_request, created_at, updated_at, deleted_at)
                VALUES (?, ?, '테스트 책상', '테스트 설명', 150000, 10000, 'DESK',
                        'A', 60, 60, 70, ?, false, NOW(6), NOW(6), NULL)
                """,
                listingId,
                sellerId,
                saleStatus
        );
    }

    private void insertOrder(final long orderId, final long listingId, final long buyerId, final String orderStatus) {
        jdbcTemplate.update(
                """
                INSERT INTO orders (id, listing_id, buyer_id, delivery_status, order_status, pending_expires_at)
                VALUES (?, ?, ?, 'PENDING', ?, NULL)
                """,
                orderId,
                listingId,
                buyerId,
                orderStatus
        );
    }
}
