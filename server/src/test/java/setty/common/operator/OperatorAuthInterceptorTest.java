package setty.common.operator;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "setty.operator.secret=" + OperatorAuthInterceptorTest.OPERATOR_SECRET)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
@DisplayName("운영자 인증")
class OperatorAuthInterceptorTest {
    static final String OPERATOR_SECRET = "test-operator-secret";

    private static final String OPERATOR_PATH = "/api/operator/dispatch-requests";
    private static final String ALLOWED_ORIGIN = "http://localhost:3000";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("브라우저의 CORS preflight는 인증 없이 통과시킨다")
    void allowsCorsPreflightWithoutSecret() throws Exception {
        mockMvc.perform(options(OPERATOR_PATH)
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", OperatorAuthInterceptor.OPERATOR_SECRET_HEADER))
                .andExpect(status().is2xxSuccessful())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN));
    }

    @Test
    @DisplayName("preflight를 통과시켜도 실제 요청은 비밀 헤더 없이 막는다")
    void stillRejectsActualRequestWithoutSecret() throws Exception {
        mockMvc.perform(get(OPERATOR_PATH).header("Origin", ALLOWED_ORIGIN))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("preflight를 통과시켜도 실제 요청은 비밀 헤더가 틀리면 막는다")
    void stillRejectsActualRequestWithWrongSecret() throws Exception {
        mockMvc.perform(get(OPERATOR_PATH)
                        .header("Origin", ALLOWED_ORIGIN)
                        .header(OperatorAuthInterceptor.OPERATOR_SECRET_HEADER, "wrong-secret"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("비밀 헤더가 맞으면 실제 요청을 처리한다")
    void allowsActualRequestWithSecret() throws Exception {
        mockMvc.perform(get(OPERATOR_PATH)
                        .header("Origin", ALLOWED_ORIGIN)
                        .header(OperatorAuthInterceptor.OPERATOR_SECRET_HEADER, OPERATOR_SECRET))
                .andExpect(status().isOk());
    }
}
