package setty.prototype;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

@DisplayName("프로토타입 인증 API")
class PrototypeAuthApiTest extends PrototypeApiSupport {
    @Test
    @DisplayName("처음 보는 번호로 로그인하면 그 번호로 가입하고 바로 로그인된다")
    void signsUpOnFirstLogin() throws Exception {
        final MockHttpSession session = logIn(SELLER_PHONE_NUMBER);

        assertThat(session).isNotNull();
        mockMvc.perform(get("/api/me/seller-page").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seller.phoneNumber").value("01000000001"));
    }

    @Test
    @DisplayName("이미 가입한 번호로 같은 비밀번호를 보내면 새로 가입하지 않고 로그인만 한다")
    void logsInExistingMember() throws Exception {
        logIn(SELLER_PHONE_NUMBER);

        final MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentialsPayload(SELLER_PHONE_NUMBER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneNumber").value("01000000001"))
                .andReturn()
                .getRequest()
                .getSession(false);

        mockMvc.perform(get("/api/me/seller-page").session(session))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("이미 가입한 번호에 다른 비밀번호를 보내면 로그인할 수 없다")
    void rejectsWrongPassword() throws Exception {
        logIn(SELLER_PHONE_NUMBER);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentialsPayload(SELLER_PHONE_NUMBER, "test-password-2")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("비밀번호를 틀려도 회원이 새로 만들어지지 않아 원래 비밀번호로 다시 로그인할 수 있다")
    void keepsFirstPasswordAfterFailedLogin() throws Exception {
        logIn(SELLER_PHONE_NUMBER);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentialsPayload(SELLER_PHONE_NUMBER, "test-password-2")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentialsPayload(SELLER_PHONE_NUMBER, PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("비밀번호가 8자 미만이면 가입도 로그인도 할 수 없다")
    void rejectsShortPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentialsPayload(SELLER_PHONE_NUMBER, "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("휴대폰 번호 형식이 맞지 않으면 가입도 로그인도 할 수 없다")
    void rejectsInvalidPhoneNumber() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentialsPayload("010-000", PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("로그아웃하면 세션이 사라져 인증이 필요한 요청을 할 수 없다")
    void logsOut() throws Exception {
        final MockHttpSession session = logIn(SELLER_PHONE_NUMBER);

        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/me/seller-page").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("로그인하지 않으면 로그아웃할 수 없다")
    void rejectsLogoutWithoutSession() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }
}
