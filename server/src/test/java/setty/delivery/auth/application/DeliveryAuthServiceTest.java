package setty.delivery.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import setty.delivery.auth.domain.DeliveryMember;
import setty.support.MySqlIntegrationTestSupport;

@SpringBootTest
class DeliveryAuthServiceTest extends MySqlIntegrationTestSupport {

    private static final Duration TOKEN_TTL = Duration.ofDays(30);

    @Autowired
    private DeliveryAuthService deliveryAuthService;

    @Autowired
    private DeliveryMemberRepository deliveryMemberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM delivery_member");
    }

    @Test
    void 로그인하면_토큰과_30일_뒤_만료_시각을_저장한다() {
        deliveryAuthService.signup(
                new SignupCommand("driver1", "password1", "010-1111-2222", "12가3456", "다마스", "000-00-00000"));
        final Instant before = Instant.now();

        final LoginResult result = deliveryAuthService.login(new LoginCommand("driver1", "password1"));

        final DeliveryMember member = deliveryMemberRepository.findByToken(result.token()).orElseThrow();
        assertThat(member.getTokenExpiresAt()).isBetween(
                before.plus(TOKEN_TTL).minusSeconds(1), Instant.now().plus(TOKEN_TTL).plusSeconds(1));
    }
}
