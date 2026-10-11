package setty.notification.keyword;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
