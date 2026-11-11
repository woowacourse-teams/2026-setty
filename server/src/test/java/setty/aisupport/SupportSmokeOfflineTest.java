package setty.aisupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;

/** Real Spring/adapter/runtime/domain SQL/store, with only the SDK network boundary replaced. */
class SupportSmokeOfflineTest extends AbstractSupportSmoke {
    @MockitoBean BedrockRuntimeClient client;
    @Override boolean live() { return false; }

    @BeforeEach void scriptedProvider() {
        AtomicInteger calls=new AtomicInteger();
        when(client.converse(any(ConverseRequest.class))).thenAnswer(invocation -> {
            ConverseRequest request=invocation.getArgument(0);
            assertThat(request.inferenceConfig().maxTokens()).isEqualTo(1024);
            assertThat(request.additionalModelRequestFields().asMap().get("thinking").asMap().get("type").asString())
                    .isEqualTo("disabled");
            int call=calls.incrementAndGet();
            if(call==1) {
                assertThat(request.toolConfig().toolChoice().tool().name()).isEqualTo("support_plan");
                var fields=new java.util.LinkedHashMap<String,Document>();
                fields.put("unsupportedTopic",Document.fromString("NONE"));fields.put("action",Document.fromString("GET_ORDER"));
                fields.put("answer",Document.fromString(""));fields.put("orderId",Document.fromNumber(930003));
                for(String name:java.util.List.of("title","fromDate","toDate","beforeId","datePreset")) fields.put(name,Document.fromNull());
                fields.put("pageSize",Document.fromNumber(5)); fields.put("purpose",Document.fromString("CANCEL_CHECK"));
                return tool("plan-1","support_plan",fields);
            }
            assertThat(call).isEqualTo(2);
            assertThat(request.toolConfig()).isNull();
            return response(StopReason.END_TURN,ContentBlock.fromText("배송원이 아직 수락하지 않아 현재 취소 요청이 가능해요. 주문 상세에서 요청할 수 있고 처리 시점에 상태가 달라질 수 있어요."));
        });
    }
    private static ConverseResponse tool(String id,String name,Map<String,Document> input) {
        return response(StopReason.TOOL_USE,ContentBlock.fromToolUse(ToolUseBlock.builder()
                .toolUseId(id).name(name).input(Document.fromMap(input)).build()));
    }
    private static ConverseResponse response(StopReason reason,ContentBlock block) {
        return ConverseResponse.builder().stopReason(reason)
                .usage(TokenUsage.builder().inputTokens(13).outputTokens(4).totalTokens(17).build())
                .output(ConverseOutput.fromMessage(Message.builder().role(ConversationRole.ASSISTANT).content(block).build())).build();
    }
}
