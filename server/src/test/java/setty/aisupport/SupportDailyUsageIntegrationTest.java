package setty.aisupport;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static setty.aisupport.SupportDtos.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import setty.common.MemberWithdrawn;
import setty.platform.OrderSupportReader;
import setty.support.MySqlIntegrationTestSupport;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "setty.ai-support.enabled=true")
@AutoConfigureMockMvc
class SupportDailyUsageIntegrationTest extends MySqlIntegrationTestSupport {
    static final Instant BEFORE_MIDNIGHT = Instant.parse("2026-11-11T14:59:59Z");
    static final LocalDate TODAY = LocalDate.of(2026, 11, 11);
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired ObjectMapper json;
    @Autowired OrderSupportReader reader;
    @Autowired SupportStore configuredStore;
    @Autowired Clock applicationClock;
    @Autowired MockMvc mvc;
    @MockitoBean SupportModel model;
    SupportStore store;

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM ai_support_conversation");
        jdbc.update("DELETE FROM ai_support_daily_usage");
        jdbc.update("DELETE FROM members WHERE id IN (910111,910112)");
        for (long member : List.of(910111L, 910112L))
            jdbc.update("INSERT INTO members(id,login_id,password,role,phone_number,address,dong_code,token,token_expires_at) VALUES(?,?,'encoded','MEMBER','010-0000-0000','가상주소','1111053000',?,TIMESTAMPADD(DAY, 30, UTC_TIMESTAMP(6)))",
                    member, "limit-" + member, "limit-token-" + member);
        store = storeAt(BEFORE_MIDNIGHT);
    }

    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM ai_support_conversation WHERE member_id IN (910111,910112)");
        jdbc.update("DELETE FROM ai_support_daily_usage WHERE member_id IN (910111,910112)");
        jdbc.update("DELETE FROM members WHERE id IN (910111,910112)");
    }

    @Test void httpLimitReturnsExact429AndHistoryRemainsReadableWithoutModel() throws Exception {
        long id = configuredStore.create(910111).id();
        jdbc.update("INSERT INTO ai_support_daily_usage VALUES(910111,?,50)", SupportDailyUsage.today(applicationClock));
        mvc.perform(post("/api/support/conversations/" + id + "/messages")
                        .header("Authorization", "Bearer limit-token-910111")
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request("blocked-http"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AI_DAILY_LIMIT"))
                .andExpect(jsonPath("$.message").value("오늘 상담 한도를 모두 사용했어요. 버튼 선택과 이전 대화 기록은 계속 이용할 수 있어요."));
        mvc.perform(get("/api/support/conversations/" + id).header("Authorization", "Bearer limit-token-910111"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        verifyNoInteractions(model);
    }

    private SupportStore storeAt(Instant now) {
        return new SupportStore(jdbc, manager, json, Clock.fixed(now, ZoneOffset.UTC), reader);
    }

    @Test void deletionDoesNotResetUsageButKstMidnightDoes() {
        long id = store.create(910111).id();
        jdbc.update("INSERT INTO ai_support_daily_usage VALUES(910111,?,49)", TODAY);
        store.accept(910111, id, request("last"));
        store.delete(910111, id);
        long next = store.create(910111).id();
        assertLimited(() -> store.accept(910111, next, request("blocked")));
        var tomorrow = storeAt(BEFORE_MIDNIGHT.plusSeconds(1));
        assertThat(tomorrow.accept(910111, next, request("tomorrow")).created()).isTrue();
        assertThat(count(910111, TODAY)).isEqualTo(50);
        assertThat(count(910111, TODAY.plusDays(1))).isEqualTo(1);
        assertThat(store.accept(910112, store.create(910112).id(), request("another-member")).created()).isTrue();
        assertThat(count(910112, TODAY)).isEqualTo(1);
    }

    @Test void concurrentDifferentConversationsCannotOverrunRemainingQuota() throws Exception {
        jdbc.update("INSERT INTO ai_support_daily_usage VALUES(910111,?,49)", TODAY);
        List<Long> conversations = new ArrayList<>();
        for (int i = 0; i < 8; i++) conversations.add(store.create(910111).id());
        var ready = new CountDownLatch(8);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(8)) {
            List<Future<Boolean>> work = new ArrayList<>();
            for (long id : conversations) work.add(pool.submit(() -> {
                ready.countDown(); start.await();
                try { return store.accept(910111, id, request("parallel")).created(); }
                catch (SupportException e) { assertThat(e.code).isEqualTo("AI_DAILY_LIMIT"); return false; }
            }));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            int accepted = 0;
            for (var task : work) if (task.get(20, TimeUnit.SECONDS)) accepted++;
            assertThat(accepted).isEqualTo(1);
        }
        assertThat(count(910111, TODAY)).isEqualTo(50);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_support_turn", Integer.class)).isEqualTo(1);
    }

    @Test void concurrentFirstUseCreatesOnlyOneCounterAndDuplicateCostsOne() throws Exception {
        long first = store.create(910111).id(), second = store.create(910111).id();
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(3)) {
            List<Future<SupportStore.Acceptance>> work = new ArrayList<>();
            for (long id : List.of(first, first, second)) work.add(pool.submit(() -> {
                start.await(); return store.accept(910111, id, request("same-id"));
            }));
            start.countDown();
            int accepted = 0;
            for (var task : work) if (task.get(20, TimeUnit.SECONDS).created()) accepted++;
            assertThat(accepted).isEqualTo(2);
        }
        assertThat(count(910111, TODAY)).isEqualTo(2);
    }

    @Test void turnAndCounterRollBackTogetherWhenAcceptanceCannotCommit() {
        long id = store.create(910111).id();
        var transaction = new org.springframework.transaction.support.TransactionTemplate(manager);
        transaction.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        assertThatThrownBy(() -> transaction.execute(status -> {
            store.accept(910111, id, request("rollback"));
            throw new IllegalStateException("synthetic rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_support_turn", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_support_daily_usage", Integer.class)).isZero();
        assertThat(store.accept(910111, id, request("rollback")).created()).isTrue();
        assertThat(count(910111, TODAY)).isEqualTo(1);
    }

    @Test void zeroLimitStillCountsButNeverBlocksAndNegativeIsRejected() {
        var unlimited = new SupportStore(jdbc, manager, json, Clock.fixed(BEFORE_MIDNIGHT, ZoneOffset.UTC), reader, 0);
        long id = unlimited.create(910111).id();
        for (int i = 0; i < 55; i++) {
            var turn = unlimited.accept(910111, id, request("unlimited-" + i)).turn();
            unlimited.complete(910111, id, turn.turnId(), new Result("답변", List.of(), 0, 0, List.of()), null, "fake", 1);
        }
        assertThat(count(910111, TODAY)).isEqualTo(55);
        assertThatThrownBy(() -> new SupportStore(jdbc, manager, json, Clock.systemUTC(), reader, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void withdrawalAndSevenDayCleanupAreIndependentOfConversationRetention() {
        jdbc.update("INSERT INTO ai_support_daily_usage VALUES(910111,?,50),(910112,?,3),(910112,?,4),(910112,?,5)",
                TODAY, TODAY.minusDays(8), TODAY.minusDays(7), TODAY.minusDays(6));
        store.withdrawn(new MemberWithdrawn(910111L));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_support_daily_usage WHERE member_id=910111", Integer.class)).isZero();
        store.purgeExpired();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_support_daily_usage", Integer.class)).isEqualTo(1);
        assertThat(count(910112, TODAY.minusDays(6))).isEqualTo(5);
    }

    private int count(long member, LocalDate date) {
        return jdbc.queryForObject("SELECT text_turns FROM ai_support_daily_usage WHERE member_id=? AND usage_date=?", Integer.class, member, date);
    }
    private static SendRequest request(String id) { return new SendRequest(id, new Input(InputKind.TEXT,"주문 문의",null,null)); }
    private static void assertLimited(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(SupportException.class, e -> {
            assertThat(e.status).isEqualTo(429); assertThat(e.code).isEqualTo("AI_DAILY_LIMIT");
        });
    }
}
