package setty.global.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import setty.support.MySqlIntegrationTestSupport;

@SpringBootTest
@AutoConfigureMockMvc
class AuthMatrixTest extends MySqlIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanUp();
        jdbcTemplate.update("""
                INSERT INTO members (id, login_id, password, role, phone_number, address, dong_code, token)
                VALUES (1, 'member1', 'encoded-password', 'MEMBER', '010-0000-0000', '가상 주소', '1111053000', 'member-token')
                """);
        jdbcTemplate.update("""
                INSERT INTO delivery_member (id, login_id, password, phone_number, license_plate_number, car_type,
                                             business_registration_number, token)
                VALUES (2, 'driver2', 'encoded-password', '010-0000-0000', '12가3456', '다마스', '000-00-00000', 'driver-token')
                """);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM members");
        jdbcTemplate.update("DELETE FROM delivery_member");
    }

    // 필터 × 배송원·없음, 공개 목록 × 없음은 ListingDeliveryFeeFilterApiTest에 있어 뺐다.
    @ParameterizedTest(name = "{0} {1} {2} → {3} {4}")
    @CsvSource(nullValues = "-", value = {
            "GET,    /api/auth/me,                       Bearer member-token,  200, -",
            "GET,    /api/auth/me,                       Bearer driver-token,  401, INVALID_TOKEN",
            "GET,    /api/auth/me,                       Bearer no-such-token, 401, INVALID_TOKEN",
            "GET,    /api/auth/me,                       member-token,         401, INVALID_TOKEN",
            "GET,    /api/auth/me,                       -,                    401, INVALID_TOKEN",
            "GET,    /api/auth/me,                       'Bearer ',            401, INVALID_TOKEN",

            "GET,    /api/delivery/shipments,            Bearer member-token,  401, INVALID_TOKEN",
            "GET,    /api/delivery/shipments,            Bearer driver-token,  200, -",
            "GET,    /api/delivery/shipments,            Bearer no-such-token, 401, INVALID_TOKEN",
            "GET,    /api/delivery/shipments,            member-token,         401, INVALID_TOKEN",
            "GET,    /api/delivery/shipments,            -,                    401, INVALID_TOKEN",

            "GET,    /api/listings,                      Bearer member-token,  200, -",
            "GET,    /api/listings,                      Bearer driver-token,  200, -",
            "GET,    /api/listings,                      Bearer no-such-token, 200, -",
            "GET,    /api/listings,                      member-token,         200, -",

            "GET,    /api/listings/999999,               Bearer member-token,  404, LISTING_NOT_FOUND",
            "GET,    /api/listings/999999,               Bearer driver-token,  404, LISTING_NOT_FOUND",
            "GET,    /api/listings/999999,               Bearer no-such-token, 404, LISTING_NOT_FOUND",
            "GET,    /api/listings/999999,               member-token,         404, LISTING_NOT_FOUND",
            "GET,    /api/listings/999999,               -,                    404, LISTING_NOT_FOUND",
            "DELETE, /api/listings/999999,               -,                    401, INVALID_TOKEN",

            "GET,    /api/listings?maxDeliveryFee=20000, Bearer member-token,  200, -",
            "GET,    /api/listings?maxDeliveryFee=20000, Bearer no-such-token, 401, INVALID_TOKEN",
            "GET,    /api/listings?maxDeliveryFee=20000, member-token,         401, INVALID_TOKEN",
    })
    void 경로와_토큰에_따른_응답(String method, String path, String authorization, int status, String code) throws Exception {
        MockHttpServletRequestBuilder builder = request(HttpMethod.valueOf(method), path);
        if (authorization != null) {
            builder.header(HttpHeaders.AUTHORIZATION, authorization);
        }

        ResultActions result = mockMvc.perform(builder).andExpect(status().is(status));
        if (code != null) {
            result.andExpect(jsonPath("$.code").value(code));
        }
    }
}
