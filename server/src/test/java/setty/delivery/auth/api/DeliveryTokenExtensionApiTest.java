package setty.delivery.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import setty.support.MySqlIntegrationTestSupport;

@SpringBootTest
@AutoConfigureMockMvc
class DeliveryTokenExtensionApiTest extends MySqlIntegrationTestSupport {

    private static final long THIRTY_DAYS = 30L * 24 * 60 * 60;
    private static final long ONE_DAY = 24L * 60 * 60;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM delivery_member");
        jdbcTemplate.update("DELETE FROM members");
    }

    @Test
    void 수명의_절반을_넘긴_배송원_토큰은_쓰이면_만료가_30일_뒤로_미뤄진다() throws Exception {
        insertDriver("driver-token", "TIMESTAMPADD(DAY, 1, UTC_TIMESTAMP(6))");

        mockMvc.perform(get("/api/delivery/shipments").header(HttpHeaders.AUTHORIZATION, "Bearer driver-token"))
                .andExpect(status().isOk());

        assertThat(remainingSeconds("delivery_member", "driver-token")).isBetween(THIRTY_DAYS - 60, THIRTY_DAYS);
    }

    @Test
    void 수명이_절반_넘게_남은_배송원_토큰은_쓰여도_그대로다() throws Exception {
        insertDriver("driver-token", "TIMESTAMPADD(DAY, 20, UTC_TIMESTAMP(6))");

        mockMvc.perform(get("/api/delivery/shipments").header(HttpHeaders.AUTHORIZATION, "Bearer driver-token"))
                .andExpect(status().isOk());

        assertThat(remainingSeconds("delivery_member", "driver-token")).isBetween(20 * ONE_DAY - 60, 20 * ONE_DAY);
    }

    @Test
    void 만료된_배송원_토큰은_쓰여도_되살아나지_않는다() throws Exception {
        insertDriver("driver-token", "TIMESTAMPADD(SECOND, -1, UTC_TIMESTAMP(6))");

        mockMvc.perform(get("/api/delivery/shipments").header(HttpHeaders.AUTHORIZATION, "Bearer driver-token"))
                .andExpect(status().isUnauthorized());

        assertThat(remainingSeconds("delivery_member", "driver-token")).isNegative();
    }

    @Test
    void 회원_토큰은_쓰여도_만료가_미뤄지지_않는다() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO members (id, login_id, password, role, phone_number, address, token, token_expires_at)
                VALUES (1, 'member1', 'encoded-password', 'MEMBER', '010-0000-0000', '가상 주소', 'member-token',
                        TIMESTAMPADD(DAY, 1, UTC_TIMESTAMP(6)))
                """);

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer member-token"))
                .andExpect(status().isOk());

        assertThat(remainingSeconds("members", "member-token")).isBetween(ONE_DAY - 60, ONE_DAY);
    }

    private void insertDriver(final String token, final String expiresAt) {
        jdbcTemplate.update("""
                INSERT INTO delivery_member (id, login_id, password, phone_number, license_plate_number, car_type,
                                             business_registration_number, token, token_expires_at)
                VALUES (1, 'driver1', 'encoded-password', '010-0000-0000', '12가3456', '다마스', '000-00-00000', ?, %s)
                """.formatted(expiresAt), token);
    }

    private long remainingSeconds(final String table, final String token) {
        return jdbcTemplate.queryForObject(
                "SELECT TIMESTAMPDIFF(SECOND, UTC_TIMESTAMP(6), token_expires_at) FROM %s WHERE token = ?"
                        .formatted(table),
                Long.class, token);
    }
}
