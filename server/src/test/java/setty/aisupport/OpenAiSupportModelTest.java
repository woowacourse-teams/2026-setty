package setty.aisupport;

import static org.assertj.core.api.Assertions.*;
import static setty.aisupport.SupportModel.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class OpenAiSupportModelTest {
    final ObjectMapper json = JsonMapper.builder().build();
    HttpServer server;
    OpenAiSupportModel model;
    AtomicInteger calls = new AtomicInteger();
    AtomicReference<String> captured = new AtomicReference<>();
    String body = """
            {"status":"completed","usage":{"input_tokens":11,"output_tokens":7},"output":[
            {"type":"message","role":"assistant","content":[{"type":"output_text","text":"확인했습니다"}]}]}
            """;
    int status = 200;
    long delay;
    boolean closeWithoutResponse;
    @BeforeEach void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/responses", exchange -> {
            calls.incrementAndGet();
            captured.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try { Thread.sleep(delay); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            if (closeWithoutResponse) { exchange.close(); return; }
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            try {
                exchange.sendResponseHeaders(status, response.length);
                exchange.getResponseBody().write(response);
            } finally { exchange.close(); }
        });
        server.start();
        model = new OpenAiSupportModel(new SupportConfiguration.Settings(true, null, null, 4, "openai"), json,
                "test-secret-never-logged", URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/responses"));
    }
    @AfterEach void cleanup() { model.close(); server.stop(0); }
    Response call() { return model.converse(List.of(new Message(Role.USER, List.of(new Text("내 주문")))),
            "system", SupportRuntime.tools(), Duration.ofSeconds(3)); }

    @Test void oneHttpRequestHasStrictNullableSchemasAndNoStoredState() {
        Response response = call();
        var request = json.readTree(captured.get());
        assertThat(calls).hasValue(1);
        assertThat(request.path("model").asString()).isEqualTo("gpt-6-luna");
        assertThat(request.path("store").asBoolean()).isFalse();
        assertThat(request.path("reasoning").path("effort").asString()).isEqualTo("none");
        assertThat(request.path("max_output_tokens").asInt()).isEqualTo(1024);
        assertThat(request.has("previous_response_id")).isFalse();
        assertThat(request.path("parallel_tool_calls").asBoolean()).isFalse();
        var tool = request.path("tools").get(0);
        assertThat(tool.path("strict").asBoolean()).isTrue();
        var parameters = tool.path("parameters");
        assertThat(parameters.path("additionalProperties").asBoolean()).isFalse();
        assertThat(parameters.path("required").size()).isEqualTo(parameters.path("properties").size());
        assertThat(parameters.path("properties").path("title").path("type").toString()).contains("null");
        assertThat(parameters.path("properties").path("datePreset").path("enum").toString()).contains("null");
        assertThat(response.outcome()).isEqualTo(Outcome.END_TURN);
        assertThat(response.usage()).isEqualTo(new Usage(11, 7));
    }
    @Test void planUsesStrictResponseSchemaAndDisablesTools() {
        model.plan(List.of(new Message(Role.USER,List.of(new Text("안녕")))),"planner",SupportPlan.schema(),Duration.ofSeconds(3));
        var request=json.readTree(captured.get());
        assertThat(request.path("tool_choice").asString()).isEqualTo("none");
        assertThat(request.path("tools").size()).isZero();
        assertThat(request.path("text").path("format").path("strict").asBoolean()).isTrue();
        assertThat(request.path("text").path("format").path("schema").path("properties").size()).isEqualTo(11);
        assertThat(calls).hasValue(1);
    }
    @Test void functionCallAndOutputReplayUsesCallIdAndPreservesJsonNull() {
        body = """
                {"status":"completed","usage":{"input_tokens":2,"output_tokens":3},"output":[
                {"type":"function_call","id":"fc_123","call_id":"call_456","name":"find_my_orders","arguments":"{\\"title\\":\\"침대\\",\\"orderId\\":null}"}]}
                """;
        Response response = call();
        assertThat(response.outcome()).isEqualTo(Outcome.TOOL_USE);
        ToolUse use = (ToolUse) response.message().content().getFirst();
        assertThat(use.id()).isEqualTo("call_456");
        assertThat(use.input()).containsEntry("title", "침대").containsEntry("orderId", null);
        var replay = json.valueToTree(model.request(List.of(response.message(), new Message(Role.USER,
                List.of(new ToolResult(use.id(), Map.of("orders", List.of()), false)))), "s", List.of()));
        assertThat(replay.path("input").get(0).path("call_id").asString()).isEqualTo("call_456");
        assertThat(replay.path("input").get(1).path("type").asString()).isEqualTo("function_call_output");
        assertThat(replay.path("input").get(1).path("call_id").asString()).isEqualTo("call_456");
        assertThat(replay.path("input").get(1).path("output").asString()).isEqualTo("{\"orders\":[]}");
    }
    @Test void refusesOpaqueReasoningRefusalMalformedArgumentsAndIncompleteButKeepsUsage() {
        for (String output : List.of(
                "{\"type\":\"reasoning\",\"summary\":[]}",
                "{\"type\":\"message\",\"role\":\"assistant\",\"content\":[{\"type\":\"refusal\",\"refusal\":\"no\"}]}",
                "{\"type\":\"function_call\",\"call_id\":\"c\",\"name\":\"find_my_orders\",\"arguments\":\"[]\"}",
                "{\"type\":\"function_call\",\"call_id\":\"c\",\"name\":\"find_my_orders\",\"arguments\":\"broken\"}")) {
            Response response = model.response(json.readTree("{\"status\":\"completed\",\"usage\":{\"input_tokens\":4,\"output_tokens\":8},\"output\":[" + output + "]}"));
            assertThat(response.message()).isNull();
            assertThat(response.usage()).isEqualTo(new Usage(4, 8));
        }
        var incomplete = model.response(json.readTree("{\"status\":\"incomplete\",\"usage\":{\"input_tokens\":4,\"output_tokens\":1024},\"output\":[]}"));
        assertThat(incomplete.outcome()).isEqualTo(Outcome.INCOMPLETE);
        assertThat(incomplete.usage().outputTokens()).isEqualTo(1024);
    }
    @Test void errorResponsesNeverRetryOrExposeRawBody() {
        for (int error : List.of(401, 429, 500, 302)) {
            status = error;
            body = "secret echoed: test-secret-never-logged";
            assertThatThrownBy(this::call).isInstanceOfSatisfying(SupportException.class, e -> {
                assertThat(e.code).isEqualTo("MODEL_UNAVAILABLE");
                assertThat(e.getMessage()).doesNotContain("secret");
                assertThat(e.getCause()).isNull();
            });
        }
        assertThat(calls).hasValue(4);
    }
    @Test void disconnectedPostIsNeverReplayed() {
        closeWithoutResponse = true;
        assertThatThrownBy(this::call).isInstanceOfSatisfying(SupportException.class,
                e -> assertThat(e.code).isEqualTo("MODEL_UNAVAILABLE"));
        assertThat(calls).hasValue(1);
    }
    @Test void timeoutDoesNotRetryAndExpiredBudgetDoesNotSend() {
        delay = 1500;
        assertThatThrownBy(() -> model.converse(List.of(), "s", List.of(), Duration.ofMillis(200)))
                .isInstanceOfSatisfying(SupportException.class, e -> assertThat(e.code).isEqualTo("PROCESSING_EXPIRED"));
        assertThat(calls).hasValue(1);
        assertThatThrownBy(() -> model.converse(List.of(), "s", List.of(), Duration.ofMillis(50)))
                .isInstanceOfSatisfying(SupportException.class, e -> assertThat(e.code).isEqualTo("PROCESSING_EXPIRED"));
        assertThat(calls).hasValue(1);
    }
    @Test void disabledNeedsNoKeyAndMakesNoRequest() {
        try (var disabled = new OpenAiSupportModel(new SupportConfiguration.Settings(false,null,null,4,"openai"),json,"")) {
            assertThatThrownBy(() -> disabled.converse(List.of(),"s",List.of(),Duration.ofSeconds(1)))
                    .isInstanceOfSatisfying(SupportException.class,e -> assertThat(e.code).isEqualTo("AI_DISABLED"));
        }
        assertThat(calls).hasValue(0);
        assertThatThrownBy(() -> new OpenAiSupportModel(new SupportConfiguration.Settings(true,null,null,4,"openai"),json,""))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void providerDefaultsPreserveBedrockAndRejectUnknownProvider() {
        assertThat(new SupportConfiguration.Settings(false,null,null,4).modelId()).isEqualTo("global.anthropic.claude-haiku-5-5");
        assertThat(new SupportConfiguration.Settings(false,null,"",4,"openai").modelId()).isEqualTo("gpt-6-luna");
        assertThatThrownBy(() -> new SupportConfiguration.Settings(false,null,null,4,"unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
