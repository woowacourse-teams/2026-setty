package setty.notification.keyword;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.notification.keyword.application.KeywordSubscriptionService;
import setty.notification.keyword.domain.KeywordConditions;
import setty.platform.listing.storage.ListingImageStorage;
import setty.support.MySqlIntegrationTestSupport;

@SpringBootTest
class KeywordSubscriptionServiceTest extends MySqlIntegrationTestSupport {

    private static final long MEMBER_ID = 801L;
    private static final long OTHER_MEMBER_ID = 802L;

    @Autowired
    private KeywordSubscriptionService keywordSubscriptionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ListingImageStorage listingImageStorage;

    @BeforeEach
    void setUp() {
        cleanUp();
        insertMember(MEMBER_ID);
        insertMember(OTHER_MEMBER_ID);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM keyword_exclusions");
        jdbcTemplate.update("DELETE FROM keyword_subscriptions");
        jdbcTemplate.update("DELETE FROM members");
    }

    @Test
    void 같은_키워드를_두_번_등록해도_한_건만_남고_예외가_없다() {
        keywordSubscriptionService.subscribe(MEMBER_ID, "아이폰");

        assertThatCode(() -> keywordSubscriptionService.subscribe(MEMBER_ID, " 아이폰 "))
                .doesNotThrowAnyException();
        assertThat(count(MEMBER_ID)).isEqualTo(1);
    }

    @Test
    void 공백뿐이거나_20자를_넘는_키워드는_거부한다() {
        assertThatThrownBy(() -> keywordSubscriptionService.subscribe(MEMBER_ID, "   "))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_KEYWORD);
        assertThatThrownBy(() -> keywordSubscriptionService.subscribe(MEMBER_ID, "가".repeat(21)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_KEYWORD);
    }

    @Test
    void 키워드는_10개까지만_등록할_수_있다() {
        for (int i = 0; i < 10; i++) {
            keywordSubscriptionService.subscribe(MEMBER_ID, "키워드" + i);
        }

        assertThatThrownBy(() -> keywordSubscriptionService.subscribe(MEMBER_ID, "열한번째"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TOO_MANY_KEYWORDS);
    }

    @Test
    void 남의_키워드는_삭제할_수_없고_내_키워드는_삭제된다() {
        keywordSubscriptionService.subscribe(MEMBER_ID, "아이폰");
        final Long id = keywordSubscriptionService.findMine(MEMBER_ID).getFirst().getId();

        assertThatThrownBy(() -> keywordSubscriptionService.unsubscribe(OTHER_MEMBER_ID, id))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.KEYWORD_NOT_FOUND);

        keywordSubscriptionService.unsubscribe(MEMBER_ID, id);
        assertThat(count(MEMBER_ID)).isZero();
    }

    @Test
    void 같은_회원이_동시에_20개를_등록해도_10개만_남는다() throws InterruptedException {
        final int attempts = 20;
        final ExecutorService executor = Executors.newFixedThreadPool(attempts);
        final CountDownLatch ready = new CountDownLatch(attempts);
        final CountDownLatch start = new CountDownLatch(1);
        final AtomicInteger rejected = new AtomicInteger();
        final AtomicInteger unexpected = new AtomicInteger();
        for (int i = 0; i < attempts; i++) {
            final String keyword = "동시" + i;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    keywordSubscriptionService.subscribe(MEMBER_ID, keyword);
                } catch (final BusinessException e) {
                    if (e.getErrorCode() == ErrorCode.TOO_MANY_KEYWORDS) {
                        rejected.incrementAndGet();
                    } else {
                        unexpected.incrementAndGet();
                    }
                } catch (final Exception e) {
                    unexpected.incrementAndGet();
                }
            });
        }
        ready.await();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(count(MEMBER_ID)).isEqualTo(10);
        assertThat(rejected.get()).isEqualTo(10);
        assertThat(unexpected.get()).isZero();
    }

    @Test
    void 조건을_설정하면_저장되고_비우면_NULL로_돌아간다() {
        keywordSubscriptionService.subscribe(MEMBER_ID, "소파");
        final Long id = keywordSubscriptionService.findMine(MEMBER_ID).getFirst().getId();

        keywordSubscriptionService.updateConditions(MEMBER_ID, id,
                KeywordConditions.of("sofa", 100_000, 300_000, 20_000, 320_000, List.of(" 케이스 ", "중고", "케이스")));

        final Map<String, Object> saved = conditions(id);
        assertThat(saved.get("category")).isEqualTo("SOFA");
        assertThat(saved.get("min_price")).isEqualTo(100_000);
        assertThat(saved.get("max_price")).isEqualTo(300_000);
        assertThat(saved.get("max_delivery_fee")).isEqualTo(20_000);
        assertThat(saved.get("max_total_price")).isEqualTo(320_000);
        assertThat(exclusions(id)).containsExactlyInAnyOrder("케이스", "중고");

        keywordSubscriptionService.updateConditions(MEMBER_ID, id, KeywordConditions.NONE);

        assertThat(conditions(id).values()).containsOnlyNulls();
        assertThat(exclusions(id)).isEmpty();
    }

    @Test
    void 남의_키워드_조건은_바꿀_수_없다() {
        keywordSubscriptionService.subscribe(MEMBER_ID, "소파");
        final Long id = keywordSubscriptionService.findMine(MEMBER_ID).getFirst().getId();

        assertThatThrownBy(() -> keywordSubscriptionService.updateConditions(OTHER_MEMBER_ID, id, KeywordConditions.NONE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.KEYWORD_NOT_FOUND);
    }

    @Test
    void 잘못된_조건은_거부한다_제외어_11개_포함() {
        keywordSubscriptionService.subscribe(MEMBER_ID, "소파");
        final Long id = keywordSubscriptionService.findMine(MEMBER_ID).getFirst().getId();

        final List<Runnable> invalid = List.of(
                () -> KeywordConditions.of(null, 300_000, 100_000, null, null, null),
                () -> KeywordConditions.of("TV", null, null, null, null, null),
                () -> KeywordConditions.of(null, -1, null, null, null, null),
                () -> KeywordConditions.of(null, null, null, 100_000_001, null, null),
                () -> KeywordConditions.of(null, null, null, null, null, List.of("가".repeat(21))),
                () -> KeywordConditions.of(null, null, null, null, null, List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k")),
                () -> keywordSubscriptionService.updateConditions(MEMBER_ID, id,
                        KeywordConditions.of(null, null, null, null, null, List.of("중고", "소파")))
        );
        for (final Runnable attempt : invalid) {
            assertThatThrownBy(attempt::run)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_KEYWORD_CONDITION);
        }
        assertThat(conditions(id).values()).containsOnlyNulls();
    }

    private Map<String, Object> conditions(final Long id) {
        return jdbcTemplate.queryForMap(
                "SELECT category, min_price, max_price, max_delivery_fee, max_total_price "
                        + "FROM keyword_subscriptions WHERE id = ?", id);
    }

    private List<String> exclusions(final Long id) {
        return jdbcTemplate.queryForList(
                "SELECT word FROM keyword_exclusions WHERE subscription_id = ? ORDER BY word", String.class, id);
    }

    private long count(final long memberId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM keyword_subscriptions WHERE member_id = ?", Long.class, memberId);
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
