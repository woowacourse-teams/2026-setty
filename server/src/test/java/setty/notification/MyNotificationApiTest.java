package setty.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import setty.platform.listing.storage.ListingImageStorage;
import setty.support.MySqlIntegrationTestSupport;

@SpringBootTest
@AutoConfigureMockMvc
class MyNotificationApiTest extends MySqlIntegrationTestSupport {

    private static final long SELLER_ID = 701L;
    private static final long MEMBER_ID = 702L;
    private static final long OTHER_MEMBER_ID = 703L;
    private static final String TOKEN = "Bearer token-" + MEMBER_ID;

    @Autowired
    private MockMvc mockMvc;

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
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM device_tokens");
        jdbcTemplate.update("DELETE FROM keyword_subscriptions");
        jdbcTemplate.update("DELETE FROM listing_notifications");
        jdbcTemplate.update("DELETE FROM listing_images");
        jdbcTemplate.update("DELETE FROM listings");
        jdbcTemplate.update("DELETE FROM members");
    }

    @Test
    void 내_알림은_삭제된_매물_제목까지_최신순으로_준다() throws Exception {
        insertListing(71L, "원목 책상", false);
        insertListing(72L, "삭제된 의자", true);
        insertNotification(7_001L, 71L, MEMBER_ID, "2026-10-01 00:00:00");
        insertNotification(7_002L, 72L, MEMBER_ID, "2026-10-02 00:00:00");
        insertNotification(7_003L, 71L, OTHER_MEMBER_ID, "2026-10-03 00:00:00");

        mockMvc.perform(get("/api/me/notifications").header(HttpHeaders.AUTHORIZATION, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(7_002))
                .andExpect(jsonPath("$.items[0].listingId").value(72))
                .andExpect(jsonPath("$.items[0].title").value("삭제된 의자"))
                .andExpect(jsonPath("$.items[1].title").value("원목 책상"));
    }

    @Test
    void 내_알림을_확인한다() throws Exception {
        insertListing(71L, "원목 책상", false);
        insertNotification(7_001L, 71L, MEMBER_ID, "2026-10-01 00:00:00");

        mockMvc.perform(post("/api/me/notifications/7001/ack").header(HttpHeaders.AUTHORIZATION, TOKEN))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT acked_at IS NOT NULL FROM listing_notifications WHERE id = 7001", Boolean.class)).isTrue();
    }

    @Test
    void 키워드를_구독하고_목록에서_보고_해지한다() throws Exception {
        mockMvc.perform(put("/api/me/keywords")
                        .header(HttpHeaders.AUTHORIZATION, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"책상\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/me/keywords").header(HttpHeaders.AUTHORIZATION, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].keyword").value("책상"));

        Long subscriptionId = jdbcTemplate.queryForObject(
                "SELECT id FROM keyword_subscriptions WHERE member_id = ?", Long.class, MEMBER_ID);
        mockMvc.perform(delete("/api/me/keywords/" + subscriptionId).header(HttpHeaders.AUTHORIZATION, TOKEN))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM keyword_subscriptions", Integer.class)).isZero();
    }

    @Test
    void 기기_토큰을_등록하고_해제한다() throws Exception {
        mockMvc.perform(put("/api/me/device-token")
                        .header(HttpHeaders.AUTHORIZATION, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"fcm-token-1\",\"platform\":\"ANDROID\"}"))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT member_id FROM device_tokens WHERE token = 'fcm-token-1'", Long.class)).isEqualTo(MEMBER_ID);

        mockMvc.perform(delete("/api/me/device-token")
                        .header(HttpHeaders.AUTHORIZATION, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"fcm-token-1\"}"))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM device_tokens", Integer.class)).isZero();
    }

    private void insertMember(long id) {
        jdbcTemplate.update("""
                INSERT INTO members (id, login_id, password, role, phone_number, address, token)
                VALUES (?, ?, 'encoded-password', 'MEMBER', '010-0000-0000', '가상 주소', ?)
                """, id, "member" + id, "token-" + id);
    }

    private void insertListing(long id, String title, boolean deleted) {
        jdbcTemplate.update("""
                INSERT INTO listings (id, seller_id, display_title, description, price, delivery_fee, category,
                                      condition_grade, width_cm, depth_cm, height_cm, sale_status,
                                      has_purchase_request, created_at, updated_at, deleted_at)
                VALUES (?, ?, ?, '테스트 설명', 150000, 10000, 'DESK',
                        'A', 60, 60, 70, 'AVAILABLE', false, NOW(6), NOW(6), IF(?, NOW(6), NULL))
                """, id, SELLER_ID, title, deleted);
    }

    private void insertNotification(long id, long listingId, long memberId, String createdAt) {
        jdbcTemplate.update("""
                INSERT INTO listing_notifications (id, listing_id, member_id, status, attempts, created_at, sent_at)
                VALUES (?, ?, ?, 'SENT', 1, ?, ?)
                """, id, listingId, memberId, createdAt, createdAt);
    }
}
