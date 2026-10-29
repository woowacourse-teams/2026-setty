package setty.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
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
import setty.deliveryfee.DeliveryFeeService;
import setty.deliveryfee.VolumeTier;
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

    @Autowired
    private DeliveryFeeService deliveryFeeService;

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
        jdbcTemplate.update("DELETE FROM keyword_exclusions");
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
                .andExpect(jsonPath("$.items[0].type").value("KEYWORD"))
                .andExpect(jsonPath("$.items[0].listingId").value(72))
                .andExpect(jsonPath("$.items[0].title").value("삭제된 의자"))
                .andExpect(jsonPath("$.items[0].price").value(150_000))
                .andExpect(jsonPath("$.items[0].deliveryFee").value(10_000))
                .andExpect(jsonPath("$.items[0].read").value(false))
                .andExpect(jsonPath("$.items[0].listingStatus").value("DELETED"))
                .andExpect(jsonPath("$.items[1].title").value("원목 책상"))
                .andExpect(jsonPath("$.items[1].listingStatus").value("AVAILABLE"));
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
    void 안_읽은_알림은_발송된_내_알림만_세고_모두_읽으면_0이_된다() throws Exception {
        insertListing(71L, "원목 책상", false);
        insertListing(72L, "의자", false);
        insertListing(73L, "소파", false);
        insertNotification(7_001L, 71L, MEMBER_ID, "2026-10-01 00:00:00");
        insertNotification(7_002L, 72L, MEMBER_ID, "2026-10-02 00:00:00");
        insertNotification(7_003L, 71L, OTHER_MEMBER_ID, "2026-10-03 00:00:00");
        jdbcTemplate.update("""
                INSERT INTO listing_notifications (id, listing_id, member_id, status, attempts, created_at)
                VALUES (7004, 73, ?, 'PENDING', 0, NOW(6))
                """, MEMBER_ID);
        jdbcTemplate.update("UPDATE listing_notifications SET acked_at = NOW(6) WHERE id = 7001");

        mockMvc.perform(get("/api/me/notifications/unread-count").header(HttpHeaders.AUTHORIZATION, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));

        mockMvc.perform(post("/api/me/notifications/read").header(HttpHeaders.AUTHORIZATION, TOKEN))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/me/notifications/unread-count").header(HttpHeaders.AUTHORIZATION, TOKEN))
                .andExpect(jsonPath("$.count").value(0));
        mockMvc.perform(get("/api/me/notifications/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-" + OTHER_MEMBER_ID))
                .andExpect(jsonPath("$.count").value(1));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT read_at IS NULL FROM listing_notifications WHERE id = 7004", Boolean.class)).isTrue();
    }

    @Test
    void 알림_하나를_읽으면_그_알림만_읽음이_되고_남의_알림은_404다() throws Exception {
        insertListing(71L, "원목 책상", false);
        insertListing(72L, "의자", false);
        insertNotification(7_001L, 71L, MEMBER_ID, "2026-10-01 00:00:00");
        insertNotification(7_002L, 72L, MEMBER_ID, "2026-10-02 00:00:00");
        insertNotification(7_003L, 71L, OTHER_MEMBER_ID, "2026-10-03 00:00:00");

        mockMvc.perform(post("/api/me/notifications/7001/read").header(HttpHeaders.AUTHORIZATION, TOKEN))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/me/notifications/7003/read").header(HttpHeaders.AUTHORIZATION, TOKEN))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/me/notifications").header(HttpHeaders.AUTHORIZATION, TOKEN))
                .andExpect(jsonPath("$.items[0].id").value(7_002))
                .andExpect(jsonPath("$.items[0].read").value(false))
                .andExpect(jsonPath("$.items[1].read").value(true));
        mockMvc.perform(get("/api/me/notifications/unread-count").header(HttpHeaders.AUTHORIZATION, TOKEN))
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void 알림함은_결제_대기와_배차를_거래중으로_묶고_판매완료를_구분한다() throws Exception {
        insertListing(71L, "결제 대기 책상", false);
        insertListing(72L, "배차된 의자", false);
        insertListing(73L, "팔린 소파", false);
        jdbcTemplate.update("UPDATE listings SET has_purchase_request = true WHERE id = 71");
        jdbcTemplate.update("UPDATE listings SET sale_status = 'RESERVED', has_purchase_request = true WHERE id = 72");
        jdbcTemplate.update("UPDATE listings SET sale_status = 'SOLD' WHERE id = 73");
        insertNotification(7_001L, 71L, MEMBER_ID, "2026-10-01 00:00:00");
        insertNotification(7_002L, 72L, MEMBER_ID, "2026-10-02 00:00:00");
        insertNotification(7_003L, 73L, MEMBER_ID, "2026-10-03 00:00:00");

        mockMvc.perform(get("/api/me/notifications").header(HttpHeaders.AUTHORIZATION, TOKEN))
                .andExpect(jsonPath("$.items[0].listingStatus").value("SOLD"))
                .andExpect(jsonPath("$.items[1].listingStatus").value("TRADING"))
                .andExpect(jsonPath("$.items[2].listingStatus").value("TRADING"));
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
    void 키워드_조건을_설정하면_목록에_실리고_남의_키워드는_404다() throws Exception {
        mockMvc.perform(put("/api/me/keywords")
                        .header(HttpHeaders.AUTHORIZATION, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"소파\"}"))
                .andExpect(status().isNoContent());
        Long subscriptionId = jdbcTemplate.queryForObject(
                "SELECT id FROM keyword_subscriptions WHERE member_id = ?", Long.class, MEMBER_ID);

        mockMvc.perform(put("/api/me/keywords/" + subscriptionId + "/conditions")
                        .header(HttpHeaders.AUTHORIZATION, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"SOFA\",\"minPrice\":100000,\"maxPrice\":300000,"
                                + "\"maxDeliveryFee\":20000,\"maxTotalPrice\":320000,\"excludeKeywords\":[\"케이스\",\"중고\"]}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/me/keywords").header(HttpHeaders.AUTHORIZATION, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].category").value("SOFA"))
                .andExpect(jsonPath("$.items[0].minPrice").value(100_000))
                .andExpect(jsonPath("$.items[0].maxPrice").value(300_000))
                .andExpect(jsonPath("$.items[0].maxDeliveryFee").value(20_000))
                .andExpect(jsonPath("$.items[0].maxTotalPrice").value(320_000))
                .andExpect(jsonPath("$.items[0].excludeKeywords", containsInAnyOrder("케이스", "중고")));

        mockMvc.perform(put("/api/me/keywords/" + subscriptionId + "/conditions")
                        .header(HttpHeaders.AUTHORIZATION, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minPrice\":300000,\"maxPrice\":100000}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/me/keywords/" + subscriptionId + "/conditions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-" + OTHER_MEMBER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 웹_기기_토큰을_등록한다() throws Exception {
        mockMvc.perform(put("/api/me/device-token")
                        .header(HttpHeaders.AUTHORIZATION, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"web-token-1\",\"platform\":\"WEB\"}"))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT platform FROM device_tokens WHERE token = 'web-token-1'", String.class)).isEqualTo("WEB");
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

    @Test
    void 같은_시각의_알림도_페이지별_순서와_읽음_상태를_유지한다() throws Exception {
        insertListing(71L, "원목 책상", false);
        insertListing(72L, "삭제된 의자", true);
        insertNotification(7_001L, 71L, MEMBER_ID, "2026-10-01 00:00:00");
        insertNotification(7_002L, 72L, MEMBER_ID, "2026-10-01 00:00:00");
        jdbcTemplate.update("UPDATE listing_notifications SET read_at = NOW(6) WHERE id = 7002");
        mockMvc.perform(get("/api/me/notifications").header(HttpHeaders.AUTHORIZATION, TOKEN)
                        .param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(7_002))
                .andExpect(jsonPath("$.items[0].title").value("삭제된 의자"))
                .andExpect(jsonPath("$.items[0].listingStatus").value("DELETED"))
                .andExpect(jsonPath("$.items[0].read").value(true));
        mockMvc.perform(get("/api/me/notifications").header(HttpHeaders.AUTHORIZATION, TOKEN)
                        .param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(7_001))
                .andExpect(jsonPath("$.items[0].read").value(false));
        mockMvc.perform(get("/api/me/notifications").header(HttpHeaders.AUTHORIZATION, TOKEN)
                        .param("page", "2").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void 알림_배송비는_받는_사람의_현재_행정동으로_계산한다() throws Exception {
        insertListing(71L, "원목 책상", false);
        insertNotification(7_001L, 71L, MEMBER_ID, "2026-10-01 00:00:00");
        jdbcTemplate.update("UPDATE listings SET dong_code = '1111053000' WHERE id = 71");
        jdbcTemplate.update("UPDATE members SET dong_code = '1156054000' WHERE id = ?", MEMBER_ID);
        int expected = deliveryFeeService.feeOrDefault("1111053000", null, "1156054000", null,
                VolumeTier.SMALL, 10_000);
        assertThat(expected).isNotEqualTo(10_000);
        mockMvc.perform(get("/api/me/notifications").header(HttpHeaders.AUTHORIZATION, TOKEN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].deliveryFee").value(expected));
        jdbcTemplate.update("UPDATE members SET dong_code = NULL WHERE id = ?", MEMBER_ID);
        mockMvc.perform(get("/api/me/notifications").header(HttpHeaders.AUTHORIZATION, TOKEN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].deliveryFee").value(10_000));
    }

    private void insertMember(long id) {
        jdbcTemplate.update("""
                INSERT INTO members (id, login_id, password, role, phone_number, address, token)
                VALUES (?, ?, 'encoded-password', 'MEMBER', '010-0000-0000', '가상 주소', ?)
                """, id, "member" + id, "token-" + id);
    }

    private void insertListing(long id, String title, boolean deleted) {
        jdbcTemplate.update("""
                INSERT INTO listings (id, seller_id, title, description, price, delivery_fee, category,
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
