package setty.notification.device;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import setty.notification.device.application.DeviceTokenService;
import setty.notification.device.domain.DevicePlatform;
import setty.platform.listing.storage.ListingImageStorage;
import setty.support.MySqlIntegrationTestSupport;

@SpringBootTest
class DeviceTokenServiceTest extends MySqlIntegrationTestSupport {

    private static final long MEMBER_ID = 701L;
    private static final long OTHER_MEMBER_ID = 702L;
    private static final String TOKEN = "fcm-token-" + "a".repeat(40);

    @Autowired
    private DeviceTokenService deviceTokenService;

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
        jdbcTemplate.update("DELETE FROM device_tokens");
        jdbcTemplate.update("DELETE FROM members");
    }

    @Test
    void 토큰을_등록하면_사용자와_플랫폼이_저장된다() {
        deviceTokenService.register(MEMBER_ID, " " + TOKEN + " ", DevicePlatform.ANDROID);

        final Map<String, Object> saved = deviceToken();
        assertThat(saved.get("member_id")).isEqualTo(MEMBER_ID);
        assertThat(saved.get("token")).isEqualTo(TOKEN);
        assertThat(saved.get("platform")).isEqualTo("ANDROID");
    }

    @Test
    void 같은_토큰을_다른_사용자가_등록하면_주인이_바뀌고_행은_하나다() {
        deviceTokenService.register(MEMBER_ID, TOKEN, DevicePlatform.ANDROID);

        deviceTokenService.register(OTHER_MEMBER_ID, TOKEN, DevicePlatform.ANDROID);

        assertThat(count()).isEqualTo(1);
        assertThat(deviceToken().get("member_id")).isEqualTo(OTHER_MEMBER_ID);
    }

    @Test
    void 내_토큰만_삭제되고_남의_토큰은_남는다() {
        deviceTokenService.register(MEMBER_ID, TOKEN, DevicePlatform.ANDROID);

        deviceTokenService.unregister(OTHER_MEMBER_ID, TOKEN);
        assertThat(count()).isEqualTo(1);

        deviceTokenService.unregister(MEMBER_ID, TOKEN);
        assertThat(count()).isZero();
    }

    private long count() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM device_tokens", Long.class);
    }

    private Map<String, Object> deviceToken() {
        return jdbcTemplate.queryForMap("SELECT member_id, token, platform FROM device_tokens");
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
