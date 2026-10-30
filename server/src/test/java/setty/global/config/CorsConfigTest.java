package setty.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.context.WebApplicationContext;

@SpringJUnitConfig(CorsConfigTest.TestConfig.class)
@ContextConfiguration(initializers = ConfigDataApplicationContextInitializer.class)
@WebAppConfiguration
class CorsConfigTest {

    private static final String LOCAL_ORIGIN = "http://localhost:3000";
    private static final String UNKNOWN_ORIGIN = "https://example.com";

    @Nested
    @ActiveProfiles("dev")
    class DevProfile extends CorsMockMvcTest {

        @ParameterizedTest
        @ValueSource(strings = {"https://www.setty.cloud", "https://setty.cloud"})
        void allowsSettyOrigins(final String origin) throws Exception {
            assertLoginAllowed(origin);
        }

        @ParameterizedTest
        @ValueSource(strings = {"https://www.setty.cloud", "https://setty.cloud"})
        void allowsSettyPreflight(final String origin) throws Exception {
            assertLoginPreflightAllowed(origin);
        }

        @Test
        void allowsLocalOrigin() throws Exception {
            mockMvc.perform(post("/api/auth/login").header(HttpHeaders.ORIGIN, LOCAL_ORIGIN))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, LOCAL_ORIGIN));
        }

        @Test
        void rejectsUnknownOrigin() throws Exception {
            mockMvc.perform(post("/api/auth/login").header(HttpHeaders.ORIGIN, UNKNOWN_ORIGIN))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @ActiveProfiles("prod")
    class ProdProfile extends CorsMockMvcTest {

        @ParameterizedTest
        @ValueSource(strings = {"https://www.setty.cloud", "https://setty.cloud"})
        void allowsSettyOrigins(final String origin) throws Exception {
            assertLoginAllowed(origin);
        }

        @ParameterizedTest
        @ValueSource(strings = {"https://www.setty.cloud", "https://setty.cloud"})
        void allowsSettyPreflight(final String origin) throws Exception {
            assertLoginPreflightAllowed(origin);
        }

        @Test
        void rejectsLocalOrigin() throws Exception {
            mockMvc.perform(post("/api/auth/login").header(HttpHeaders.ORIGIN, LOCAL_ORIGIN))
                    .andExpect(status().isForbidden());
        }

        @Test
        void rejectsUnknownOrigin() throws Exception {
            mockMvc.perform(post("/api/auth/login").header(HttpHeaders.ORIGIN, UNKNOWN_ORIGIN))
                    .andExpect(status().isForbidden());
        }
    }

    abstract static class CorsMockMvcTest {

        @Autowired
        private WebApplicationContext context;

        protected MockMvc mockMvc;

        @BeforeEach
        void setUp() {
            mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        }

        protected void assertLoginAllowed(final String origin) throws Exception {
            mockMvc.perform(post("/api/auth/login").header(HttpHeaders.ORIGIN, origin))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin));
        }

        protected void assertLoginPreflightAllowed(final String origin) throws Exception {
            mockMvc.perform(options("/api/auth/login")
                            .header(HttpHeaders.ORIGIN, origin)
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "content-type"));
        }
    }

    // 바깥 클래스에 @Test가 없으면 @SpringBootTest 스캔에서 자동 제외되지 않으므로 테스트 전용 컴포넌트임을 명시한다.
    @TestComponent
    @RestController
    static class TestController {

        @PostMapping("/api/auth/login")
        void login() {
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import({CorsConfig.class, TestController.class})
    static class TestConfig {
    }
}
