package setty.aisupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.security.MessageDigest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.mysql.MySQLContainer;
import static setty.aisupport.SupportModel.*;
import tools.jackson.databind.ObjectMapper;
import javax.sql.DataSource;

/** One synthetic HTTP turn; each context owns its disposable database, never a configured dev DB. */
@SpringBootTest(properties = {
        "spring.profiles.active=default", "server.address=127.0.0.1",
        "setty.ai-support.enabled=true", "setty.ai-support.max-concurrent=1",
        "setty.ai-support.region=ap-northeast-2", "setty.ai-support.model-id=global.anthropic.claude-haiku-5-5",
        "setty.order.pending-expiration.scan-interval=PT24H", "setty.order.completion.scan-interval=PT24H",
        "setty.events.resubmission-interval=PT24H", "setty.address.backfill.enabled=false",
        "setty.listing.zip-zone-backfill.enabled=false", "setty.delivery-fee.zone-mode=dong",
        "setty.notification.sender=noop", "management.otlp.metrics.export.enabled=false"
})
@AutoConfigureMockMvc
@Import(AbstractSupportSmoke.IsolatedConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class AbstractSupportSmoke {
    static final String APPROVAL = "one-synthetic-turn-max-four-model-calls";
    static final String INPUT = "930003번 평가 배송전침대 주문 취소 가능해?";
    static final String TOKEN = "Bearer support-eval-buyer-token";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired MySQLContainer smokeDatabase;
    @Autowired ObjectMapper json;
    @Autowired Probe probe;
    abstract boolean live();

    @Test
    void oneSyntheticTurnProposesTargetAndPersistsWithoutBusinessMutation() throws Exception {
        // This check is independent of the class-level live test condition.
        if (live() && !APPROVAL.equals(System.getenv("SETTY_AI_LIVE_SMOKE_APPROVED")))
            throw new IllegalStateException("Live smoke approval is missing");
        Map<String,Object> evidence = new LinkedHashMap<>();
        evidence.put("mode", live() ? "LIVE" : "OFFLINE_SCRIPTED_PROVIDER");
        evidence.put("startedAt", Instant.now().toString());
        evidence.put("modelId", "global.anthropic.claude-haiku-5-5");
        evidence.put("region", "ap-northeast-2");
        evidence.put("promptVersion", SupportRuntime.PROMPT_VERSION);
        evidence.put("input", INPUT);
        evidence.put("maxModelAttempts", 4);
        evidence.put("status", "FAIL");
        try {
            try (var connection=dataSource.getConnection()) {
                assertThat(connection.getMetaData().getURL()).isEqualTo(smokeDatabase.getJdbcUrl());
            }
            assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("setty_test");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM EVENT_PUBLICATION", Long.class)).isZero();
            var fixture = new ClassPathResource("aisupport/eval-fixture.sql");
            try(var input=fixture.getInputStream()) {
                evidence.put("fixtureSha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(input.readAllBytes())));
            }
            new ResourceDatabasePopulator(fixture).execute(dataSource);
            var before = businessState();
            evidence.put("businessBefore", before);
            String created = mvc.perform(post("/api/support/conversations").header("Authorization",TOKEN))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            long conversation = json.readTree(created).get("id").asLong();
            String request = json.writeValueAsString(new SupportDtos.SendRequest("smoke-one-turn",
                    new SupportDtos.Input(SupportDtos.InputKind.TEXT, INPUT, null, null)));
            // Exactly one message POST. Do not retry a failure or add a clarification turn.
            var response = mvc.perform(post("/api/support/conversations/"+conversation+"/messages")
                    .header("Authorization",TOKEN).contentType(MediaType.APPLICATION_JSON).content(request))
                    .andReturn().getResponse();
            evidence.put("httpStatus",response.getStatus());
            evidence.put("response",json.readTree(response.getContentAsString()));
            var after = businessState();
            evidence.put("businessAfter",after);
            var stored = jdbc.queryForList("SELECT status,error_code,model_id,prompt_version,input_tokens,output_tokens,"
                    + "duration_ms,tool_summary_json FROM ai_support_turn WHERE conversation_id=?",conversation);
            evidence.put("storedTurns",stored);
            assertThat(after).isEqualTo(before);
            assertThat(response.getStatus()).isEqualTo(200);
            var body = json.readTree(response.getContentAsString());
            assertThat(body.get("status").asText()).isEqualTo("SUCCEEDED");
            assertThat(body.get("answer").asText()).isNotBlank().doesNotContain("취소 완료했습니다");
            assertThat(body.get("cards").toString()).contains("930003", "ORDERS", "\"confirmLatest\":true").doesNotContain("cancellationOffer");
            assertThat(stored).hasSize(1);
            assertThat(stored.getFirst().get("status")).isEqualTo("SUCCEEDED");
            var audit = json.readTree(stored.getFirst().get("tool_summary_json").toString());
            List<String> successfulTools = new ArrayList<>();
            for (var item : audit) {
                if ("plan".equals(item.path("name").asText())) continue;
                assertThat(item.get("outcome").asText()).isEqualTo("SUCCESS");
                successfulTools.add(item.get("name").asText());
            }
            assertThat(successfulTools).containsExactly("confirm_order_target");
            assertThat(probe.attempts).isBetween(1,4);
            mvc.perform(get("/api/support/conversations/"+conversation).header("Authorization",TOKEN))
                    .andExpect(status().isOk());
            evidence.put("status","PASS");
        } finally {
            evidence.put("modelAttempts",probe.attempts);
            evidence.put("providerResponses",probe.responses);
            evidence.put("usageUnknown",probe.responses.size()!=probe.attempts);
            evidence.put("finishedAt",Instant.now().toString());
            Path destination=Path.of("build","support-smoke",live()?"live-result.json":"offline-result.json");
            Files.createDirectories(destination.getParent());
            Files.writeString(destination,json.writerWithDefaultPrettyPrinter().writeValueAsString(evidence));
        }
    }

    private List<Map<String,Object>> businessState() {
        return jdbc.queryForList("SELECT o.id,o.buyer_id,o.order_status,o.delivery_status,o.created_at,d.status "
                + "FROM orders o LEFT JOIN delivery d ON d.order_id=o.id ORDER BY o.id");
    }

    @TestConfiguration(proxyBeanMethods=false)
    static class IsolatedConfiguration {
        @Bean @ServiceConnection
        MySQLContainer smokeDatabase() {
            return new MySQLContainer("mysql:8.4.11").withDatabaseName("setty_test")
                    .withUsername("setty_test").withPassword("setty_test");
        }
        @Bean @Primary
        Probe smokeProbe(BedrockSupportModel delegate) { return new Probe(delegate); }
    }

    static final class Probe implements SupportModel {
        private final BedrockSupportModel delegate;
        int attempts;
        final List<Map<String,Object>> responses = new ArrayList<>();
        Probe(BedrockSupportModel delegate) { this.delegate=delegate; }
        public Response plan(List<Message> messages, String system, Map<String,Object> schema, Duration remaining) {
            return record(() -> delegate.plan(messages,system,schema,remaining));
        }
        public Response converse(List<Message> messages, String system, List<Tool> tools, Duration remaining) {
            return record(() -> delegate.converse(messages,system,tools,remaining));
        }
        private Response record(java.util.function.Supplier<Response> operation) {
            if (attempts>=4) throw new IllegalStateException("Smoke model request budget exhausted");
            attempts++;
            Response response=operation.get();
            Map<String,Object> entry=new LinkedHashMap<>();
            entry.put("stopReason",response.outcome().name());
            if(response.usage()!=null) {
                entry.put("inputTokens",response.usage().inputTokens());
                entry.put("outputTokens",response.usage().outputTokens());
            }
            responses.add(entry);
            return response;
        }
    }
}
