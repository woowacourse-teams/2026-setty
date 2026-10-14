package setty.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import setty.support.MySqlIntegrationTestSupport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.address=127.0.0.1")
class ErrorResponseContentTypeHttpTest extends MySqlIntegrationTestSupport {

    private static final String DRIVER_TOKEN = "error-json-test-driver-token";

    @Autowired
    private Environment environment;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @BeforeEach
    void setUp() {
        cleanUp();
        jdbcTemplate.update("""
                INSERT INTO delivery_member (login_id, password, phone_number, license_plate_number, car_type,
                                             business_registration_number, token)
                VALUES ('error_json_driver', 'encoded-password', '010-0000-0000', '12가3456', '다마스', '000-00-00000', ?)
                """, DRIVER_TOKEN);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM delivery_member WHERE token = ?", DRIVER_TOKEN);
    }

    @ParameterizedTest(name = "{0} {1} Accept={2} → {3} {4}")
    @CsvSource(nullValues = "-", value = {
            "/api/listings/999999, -,                                   text/html,        404, LISTING_NOT_FOUND",
            "/api/listings/999999, -,                                   application/json, 404, LISTING_NOT_FOUND",
            "/api/listings/999999, -,                                   -,                404, LISTING_NOT_FOUND",
            "/api/auth/me,         Bearer error-json-test-driver-token, text/html,        401, INVALID_TOKEN",
            "/api/auth/me,         Bearer error-json-test-driver-token, application/json, 401, INVALID_TOKEN",
            "/api/auth/me,         -,                                   text/html,        401, INVALID_TOKEN",
            "/api/auth/me,         -,                                   application/json, 401, INVALID_TOKEN",
            "/api/auth/me,         -,                                   -,                401, INVALID_TOKEN",
    })
    void 오류는_Accept와_상관없이_원래_상태와_JSON_본문으로_나간다(
            String path, String authorization, String accept, int status, String code) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl() + path)).GET();
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        if (accept != null) {
            request.header("Accept", accept);
        }

        HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(MediaType.parseMediaType(response.headers().firstValue("Content-Type").orElseThrow())
                .isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.path("code").asString()).isEqualTo(code);
        assertThat(body.path("message").asString()).isNotBlank();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + environment.getRequiredProperty("local.server.port");
    }
}
