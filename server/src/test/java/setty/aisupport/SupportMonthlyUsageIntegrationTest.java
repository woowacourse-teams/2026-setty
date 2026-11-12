package setty.aisupport;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static setty.aisupport.SupportDtos.*;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import setty.platform.OrderSupportReader;
import setty.support.MySqlIntegrationTestSupport;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
    "setty.ai-support.enabled=true",
    "setty.ai-support.monthly-text-limit=30000"
})
@AutoConfigureMockMvc
class SupportMonthlyUsageIntegrationTest extends MySqlIntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired ObjectMapper json;
    @Autowired OrderSupportReader reader;
    @Autowired SupportStore store;
    @Autowired Clock clock;
    @Autowired MockMvc mvc;
    @MockitoBean SupportModel model;
    LocalDate today;

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM ai_support_conversation");
        jdbc.update("DELETE FROM ai_support_daily_usage");
        jdbc.update("DELETE FROM members WHERE id IN (910121,910122)");
        for (long member : List.of(910121L, 910122L))
            jdbc.update("INSERT INTO members(id,login_id,password,role,phone_number,address,dong_code,token,token_expires_at) VALUES(?,?,'encoded','MEMBER','010-0000-0000','가상주소','1111053000',?,TIMESTAMPADD(DAY, 30, UTC_TIMESTAMP(6)))",
                    member, "monthly-" + member, "monthly-token-" + member);
        today = SupportDailyUsage.today(clock);
    }

    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM ai_support_conversation WHERE member_id IN (910121,910122)");
        jdbc.update("DELETE FROM ai_support_daily_usage WHERE member_id IN (910121,910122)");
        jdbc.update("DELETE FROM members WHERE id IN (910121,910122)");
    }

    @Test void monthlyLimitReturns429WithoutModelTurnOrDailyIncrement() throws Exception {
        long id = store.create(910121).id();
        jdbc.update("INSERT INTO ai_support_daily_usage VALUES(910121,?,49),(910122,?,29951)",
                today, today.withDayOfMonth(1));

        mvc.perform(post("/api/support/conversations/" + id + "/messages")
                        .header("Authorization", "Bearer monthly-token-910121")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request("blocked"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AI_MONTHLY_LIMIT"))
                .andExpect(jsonPath("$.message").value("이번 달 상담 사용량을 모두 사용했어요. 버튼 선택과 이전 대화 기록은 계속 이용할 수 있어요."));
        verifyNoInteractions(model);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_support_turn WHERE conversation_id=?", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT text_turns FROM ai_support_daily_usage WHERE member_id=910121 AND usage_date=?", Integer.class, today)).isEqualTo(49);
        mvc.perform(get("/api/support/conversations/" + id)
                        .header("Authorization", "Bearer monthly-token-910121"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test void zeroMonthlyLimitAcceptsTextBeyondDefaultLimit() {
        jdbc.update("INSERT INTO ai_support_daily_usage VALUES(910122,?,30000)", today);
        var unlimited = new SupportStore(jdbc, manager, json, clock, reader, 50, 0);
        for (int i = 0; i < 2; i++) {
            long id = unlimited.create(910121).id();
            assertThat(unlimited.accept(910121, id, request("unlimited-" + i)).created()).isTrue();
        }
        assertThat(jdbc.queryForObject("SELECT SUM(text_turns) FROM ai_support_daily_usage", Long.class)).isEqualTo(30002L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_support_turn", Integer.class)).isEqualTo(2);
    }

    private static SendRequest request(String id) {
        return new SendRequest(id, new Input(InputKind.TEXT, "주문 문의", null, null));
    }
}
