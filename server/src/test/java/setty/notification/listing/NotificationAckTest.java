package setty.notification.listing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.notification.listing.application.ListingNotificationService;
import setty.platform.listing.storage.ListingImageStorage;
import setty.support.MySqlIntegrationTestSupport;

@SpringBootTest
class NotificationAckTest extends MySqlIntegrationTestSupport {

    private static final long SELLER_ID = 601L;
    private static final long MEMBER_ID = 602L;
    private static final long OTHER_MEMBER_ID = 603L;
    private static final long LISTING_ID = 61L;
    private static final long NOTIFICATION_ID = 6_001L;

    @Autowired
    private ListingNotificationService listingNotificationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ListingImageStorage listingImageStorage;

    @BeforeEach
    void setUp() {
        cleanUp();
        insertMember(SELLER_ID);
        insertMember(MEMBER_ID);
        insertMember(OTHER_MEMBER_ID);
        jdbcTemplate.update(
                """
                INSERT INTO listings (id, seller_id, display_title, description, price, delivery_fee, category,
                                      condition_grade, width_cm, depth_cm, height_cm, sale_status,
                                      has_purchase_request, created_at, updated_at, deleted_at)
                VALUES (?, ?, '테스트 책상', '테스트 설명', 150000, 10000, 'DESK',
                        'A', 60, 60, 70, 'AVAILABLE', false, NOW(6), NOW(6), NULL)
                """,
                LISTING_ID, SELLER_ID);
        jdbcTemplate.update(
                """
                INSERT INTO listing_notifications (id, listing_id, member_id, status, attempts, created_at, sent_at)
                VALUES (?, ?, ?, 'SENT', 1, NOW(6), NOW(6))
                """,
                NOTIFICATION_ID, LISTING_ID, MEMBER_ID);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM listing_notifications");
        jdbcTemplate.update("DELETE FROM listing_images");
        jdbcTemplate.update("DELETE FROM listings");
        jdbcTemplate.update("DELETE FROM members");
    }

    @Test
    void 수신_확인하면_서버_시각이_기록된다() {
        listingNotificationService.ack(MEMBER_ID, NOTIFICATION_ID);

        assertThat(ackedAt()).isNotNull();
    }

    @Test
    void 두_번_확인해도_처음_시각이_유지된다() {
        listingNotificationService.ack(MEMBER_ID, NOTIFICATION_ID);
        final Timestamp first = ackedAt();

        listingNotificationService.ack(MEMBER_ID, NOTIFICATION_ID);

        assertThat(ackedAt()).isEqualTo(first);
    }

    @Test
    void 남의_알림은_확인할_수_없다() {
        assertThatThrownBy(() -> listingNotificationService.ack(OTHER_MEMBER_ID, NOTIFICATION_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
        assertThat(ackedAt()).isNull();
    }

    private Timestamp ackedAt() {
        return jdbcTemplate.queryForObject(
                "SELECT acked_at FROM listing_notifications WHERE id = ?", Timestamp.class, NOTIFICATION_ID);
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
