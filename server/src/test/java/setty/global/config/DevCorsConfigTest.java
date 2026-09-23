package setty.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.context.WebApplicationContext;

@SpringJUnitConfig(DevCorsConfigTest.TestConfig.class)
@WebAppConfiguration
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "setty.cors.allowed-origins=http://localhost:3000,https://www.setty.cloud"
})
class DevCorsConfigTest {

    private static final String LOCAL_ORIGIN = "http://localhost:3000";
    private static final String PRODUCTION_ORIGIN = "https://www.setty.cloud";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void allowsProductionOrigin() throws Exception {
        mockMvc.perform(post("/api/auth/login").header(HttpHeaders.ORIGIN, PRODUCTION_ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, PRODUCTION_ORIGIN));
    }

    @Test
    void allowsLocalOrigin() throws Exception {
        mockMvc.perform(post("/api/auth/login").header(HttpHeaders.ORIGIN, LOCAL_ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, LOCAL_ORIGIN));
    }

    @Test
    void rejectsUnknownOrigin() throws Exception {
        mockMvc.perform(post("/api/auth/login").header(HttpHeaders.ORIGIN, "https://example.com"))
                .andExpect(status().isForbidden());
    }

    @RestController
    static class TestController {

        @PostMapping("/api/auth/login")
        void login() {
        }
    }

    @Configuration
    @EnableWebMvc
    @Import({DevCorsConfig.class, TestController.class})
    static class TestConfig {
    }
}
