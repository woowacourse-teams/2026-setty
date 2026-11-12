package setty.settlement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import setty.platform.listing.storage.ListingImageStorage;
import setty.support.MySqlIntegrationTestSupport;

@SpringBootTest
@AutoConfigureMockMvc
class SettlementQueryApiTest extends MySqlIntegrationTestSupport {

    private static final long SELLER_ID = 801L;
    private static final long OTHER_SELLER_ID = 802L;
    private static final long DRIVER_ID = 901L;
    private static final long OTHER_DRIVER_ID = 902L;

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
        insertMember(OTHER_SELLER_ID);
        insertDriver(DRIVER_ID);
        insertDriver(OTHER_DRIVER_ID);
        insertSettlement(1L, "SELLER", SELLER_ID, 11L, 100_000, "CONFIRMED", "2026-11-01 10:00:00");
        insertSettlement(2L, "SELLER", SELLER_ID, 12L, 50_000, "CANCELLED", "2026-11-02 10:00:00");
        insertSettlement(3L, "SELLER", SELLER_ID, 13L, 70_000, "PENDING", null);
        insertSettlement(4L, "SELLER", OTHER_SELLER_ID, 14L, 999_000, "CONFIRMED", "2026-11-03 10:00:00");
        insertSettlement(1L, "DRIVER", DRIVER_ID, null, 10_000, "CONFIRMED", "2026-11-01 10:00:00");
        insertSettlement(3L, "DRIVER", DRIVER_ID, null, 12_000, "CONFIRMED", "2026-11-03 10:00:00");
        insertSettlement(4L, "DRIVER", OTHER_DRIVER_ID, null, 999_000, "CONFIRMED", "2026-11-03 10:00:00");
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM settlements");
        jdbcTemplate.update("DELETE FROM members");
        jdbcTemplate.update("DELETE FROM delivery_member");
    }

    @Test
    void 판매자는_자기_정산만_최신순으로_보고_확정_금액_합을_받는다() throws Exception {
        mockMvc.perform(get("/api/me/settlements").header(HttpHeaders.AUTHORIZATION, "Bearer token-" + SELLER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedTotal").value(100_000))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].orderId").value(3))
                .andExpect(jsonPath("$.items[0].listingId").value(13))
                .andExpect(jsonPath("$.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.items[0].confirmedAt").value((Object) null))
                .andExpect(jsonPath("$.items[1].status").value("CANCELLED"))
                .andExpect(jsonPath("$.items[1].cancelledAt").isNotEmpty())
                .andExpect(jsonPath("$.items[2].orderId").value(1))
                .andExpect(jsonPath("$.items[2].amount").value(100_000))
                .andExpect(jsonPath("$.items[2].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.items[2].confirmedAt").isNotEmpty());
    }

    @Test
    void 기사는_자기_정산만_보고_매물_없이_받는다() throws Exception {
        mockMvc.perform(get("/api/delivery/settlements").header(HttpHeaders.AUTHORIZATION, "Bearer driver-token-" + DRIVER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedTotal").value(22_000))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].orderId").value(3))
                .andExpect(jsonPath("$.items[0].listingId").value((Object) null))
                .andExpect(jsonPath("$.items[1].orderId").value(1));
    }

    @Test
    void 정산이_없으면_빈_목록과_합_0을_받는다() throws Exception {
        insertMember(803L);

        mockMvc.perform(get("/api/me/settlements").header(HttpHeaders.AUTHORIZATION, "Bearer token-803"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedTotal").value(0))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void 로그인하지_않으면_정산을_볼_수_없다() throws Exception {
        mockMvc.perform(get("/api/me/settlements")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/delivery/settlements")).andExpect(status().isUnauthorized());
    }

    private void insertSettlement(
            final long orderId,
            final String payeeType,
            final long payeeId,
            final Long listingId,
            final int amount,
            final String status,
            final String decidedAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO settlements (order_id, payee_type, payee_id, listing_id, amount, status, created_at,
                                         confirmed_at, cancelled_at)
                VALUES (?, ?, ?, ?, ?, ?, NOW(6), ?, ?)
                """,
                orderId, payeeType, payeeId, listingId, amount, status,
                "CONFIRMED".equals(status) ? decidedAt : null,
                "CANCELLED".equals(status) ? decidedAt : null);
    }

    private void insertMember(final long memberId) {
        jdbcTemplate.update(
                """
                INSERT INTO members (id, login_id, password, role, phone_number, address, token, token_expires_at)
                VALUES (?, ?, 'encoded-password', 'MEMBER', '010-0000-0000', '가상 주소', ?,
                        TIMESTAMPADD(DAY, 1, UTC_TIMESTAMP(6)))
                """,
                memberId, "member" + memberId, "token-" + memberId);
    }

    private void insertDriver(final long driverId) {
        jdbcTemplate.update(
                """
                INSERT INTO delivery_member (id, login_id, password, phone_number, license_plate_number, car_type,
                                             business_registration_number, token, token_expires_at)
                VALUES (?, ?, 'encoded-password', '010-0000-0000', '12가3456', '다마스', '000-00-00000', ?,
                        TIMESTAMPADD(DAY, 1, UTC_TIMESTAMP(6)))
                """,
                driverId, "driver" + driverId, "driver-token-" + driverId);
    }
}
