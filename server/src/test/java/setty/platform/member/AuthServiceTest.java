package setty.platform.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.platform.member.controller.dto.MemberMeResponse;
import setty.platform.member.controller.dto.UpdateProfileRequest;
import setty.platform.member.domain.Member;
import setty.platform.member.repository.MemberRepository;
import setty.platform.member.service.AuthService;

@SpringBootTest
@Testcontainers
class AuthServiceTest {

    private static final long MEMBER_ID = 101L;
    private static final String TOKEN = "token-101";

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.11")
            .withDatabaseName("setty_test")
            .withUsername("setty_test")
            .withPassword("setty_test");

    @Autowired
    private AuthService authService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanUp();
        insertMember(MEMBER_ID, TOKEN);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM members");
    }

    @Test
    void 로그아웃하면_토큰이_무효화된다() {
        authService.logout(MEMBER_ID);

        assertThat(memberRepository.findByToken(TOKEN)).isEmpty();
    }

    @Test
    void 존재하지_않는_회원_로그아웃은_거부된다() {
        assertThatThrownBy(() -> authService.logout(999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    void 연락처와_주소를_수정한다() {
        final MemberMeResponse response = authService.updateProfile(
                MEMBER_ID, new UpdateProfileRequest("010-1111-2222", "서울시 새주소구 변경로 1"));

        assertThat(response.phoneNumber()).isEqualTo("010-1111-2222");
        assertThat(response.address()).isEqualTo("서울시 새주소구 변경로 1");

        final Member reloaded = memberRepository.findById(MEMBER_ID).orElseThrow();
        assertThat(reloaded.getPhoneNumber()).isEqualTo("010-1111-2222");
        assertThat(reloaded.getAddress()).isEqualTo("서울시 새주소구 변경로 1");
    }

    @Test
    void 존재하지_않는_회원_정보수정은_거부된다() {
        assertThatThrownBy(() -> authService.updateProfile(
                999L, new UpdateProfileRequest("010-1111-2222", "서울시 새주소구 변경로 1")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    private void insertMember(final long memberId, final String token) {
        jdbcTemplate.update(
                """
                INSERT INTO members (id, login_id, password, role, phone_number, address, token)
                VALUES (?, ?, 'encoded-password', 'MEMBER', '010-0000-0000', '가상 주소', ?)
                """,
                memberId,
                "member" + memberId,
                token
        );
    }
}
