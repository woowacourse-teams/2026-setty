package setty.prototype;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import setty.prototype.repository.MemberRepository;

/**
 * 세션 쿠키 속성은 서블릿 컨테이너가 붙이므로 MockMvc로는 확인할 수 없다.
 * 실제 포트를 띄워 계약 문서의 쿠키 속성을 확인한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("프로토타입 세션 쿠키")
class PrototypeSessionCookieTest {
    private static final String PHONE_NUMBER = "010-0000-0009";
    private static final String NORMALIZED_PHONE_NUMBER = "01000000009";

    @LocalServerPort
    private int port;

    @Autowired
    private MemberRepository memberRepository;

    @AfterEach
    void tearDown() {
        memberRepository.findByPhoneNumber(NORMALIZED_PHONE_NUMBER).ifPresent(memberRepository::delete);
    }

    @Test
    @DisplayName("로그인하면 HttpOnly·SameSite=Lax 세션 쿠키를 내려준다")
    void issuesHttpOnlyLaxSessionCookie() throws Exception {
        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {
                          "phoneNumber": "%s",
                          "password": "1234"
                        }
                        """.formatted(PHONE_NUMBER)))
                .build();

        final HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().allValues("Set-Cookie"))
                .anySatisfy(cookie -> assertThat(cookie)
                        .startsWith("JSESSIONID=")
                        .contains("Path=/")
                        .contains("HttpOnly")
                        .contains("SameSite=Lax"));
    }
}
