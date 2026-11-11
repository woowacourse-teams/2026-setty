package setty.aisupport;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;

class BedrockSupportModelTest {
    @org.junit.jupiter.api.Test void plannerForcesOnlyPlanToolAndExplanationOmitsToolConfig() {
        var client=mock(BedrockRuntimeClient.class);
        var model=new BedrockSupportModel(client,new SupportConfiguration.Settings(true,null,null,1));
        model.plan(java.util.List.of(),"planner",SupportPlan.schema(),java.time.Duration.ofSeconds(5));
        model.converse(java.util.List.of(),"explain",java.util.List.of(),java.time.Duration.ofSeconds(5));
        var captured=org.mockito.ArgumentCaptor.forClass(ConverseRequest.class);
        verify(client,org.mockito.Mockito.times(2)).converse(captured.capture());
        assertThat(captured.getAllValues().get(0).toolConfig().toolChoice().tool().name()).isEqualTo("support_plan");
        assertThat(captured.getAllValues().get(0).toolConfig().tools()).hasSize(1);
        assertThat(captured.getAllValues().get(1).toolConfig()).isNull();
    }
    @Test void disabledAdapterNeverCallsAws() {
        var client = mock(BedrockRuntimeClient.class);
        var model = new BedrockSupportModel(client,new SupportConfiguration.Settings(false,null,null,4));
        assertThatThrownBy(()->model.converse(List.of(),"system",SupportRuntime.tools(),Duration.ofSeconds(10)))
                .isInstanceOfSatisfying(SupportException.class,e->assertThat(e.code).isEqualTo("AI_DISABLED"));
        verifyNoInteractions(client);
    }
    @Test void requestUsesConfiguredModelDisabledThinkingAndRemainingDeadline() {
        var client = mock(BedrockRuntimeClient.class);
        var model = new BedrockSupportModel(client,new SupportConfiguration.Settings(true,"ap-northeast-2","global.anthropic.claude-haiku-5-5",4));
        model.converse(List.of(new SupportModel.Message(SupportModel.Role.USER,List.of(new SupportModel.Text("주문 문의")))),"system",SupportRuntime.tools(),Duration.ofSeconds(7));
        var captor = ArgumentCaptor.forClass(ConverseRequest.class);
        verify(client).converse(captor.capture());
        ConverseRequest request = captor.getValue();
        assertThat(request.modelId()).isEqualTo("global.anthropic.claude-haiku-5-5");
        assertThat(request.inferenceConfig().maxTokens()).isEqualTo(1024);
        assertThat(request.overrideConfiguration().orElseThrow().apiCallTimeout()).contains(Duration.ofSeconds(7));
        assertThat(request.additionalModelRequestFields().asMap().get("thinking").asMap().get("type").asString()).isEqualTo("disabled");
    }

    @Test void roundTripKeepsOrderedTextToolIdsJsonAndErrorResultWithoutProviderTypes() {
        var client=mock(BedrockRuntimeClient.class);
        var model=new BedrockSupportModel(client,new SupportConfiguration.Settings(true,null,null,1));
        var awsUse=ToolUseBlock.builder().toolUseId("next-id").name("get_order_support_context")
                .input(software.amazon.awssdk.core.document.Document.fromMap(Map.of("orderId",
                        software.amazon.awssdk.core.document.Document.fromNumber(930003)))).build();
        when(client.converse(any(ConverseRequest.class))).thenReturn(ConverseResponse.builder().stopReason(StopReason.TOOL_USE)
                .usage(TokenUsage.builder().inputTokens(17).outputTokens(9).totalTokens(26).build())
                .output(ConverseOutput.fromMessage(Message.builder().role(ConversationRole.ASSISTANT)
                        .content(ContentBlock.fromText("확인 중"),ContentBlock.fromToolUse(awsUse)).build())).build());
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("missing",null); result.put("ok",false); result.put("items",List.of(Map.of("id",930003)));
        var reply=model.converse(List.of(
                new SupportModel.Message(SupportModel.Role.USER,List.of(new SupportModel.Text("주문"))),
                new SupportModel.Message(SupportModel.Role.ASSISTANT,List.of(new SupportModel.Text("조회"),
                        new SupportModel.ToolUse("first-id","find_my_orders",Map.of("title","침대")))),
                new SupportModel.Message(SupportModel.Role.USER,List.of(new SupportModel.ToolResult("first-id",result,true)))
        ),"system",SupportRuntime.tools(),Duration.ofSeconds(7));
        assertThat(reply.outcome()).isEqualTo(SupportModel.Outcome.TOOL_USE);
        assertThat(reply.usage()).isEqualTo(new SupportModel.Usage(17,9));
        assertThat(reply.message().content().getFirst()).isEqualTo(new SupportModel.Text("확인 중"));
        var use=(SupportModel.ToolUse)reply.message().content().getLast();
        assertThat(use.id()).isEqualTo("next-id");
        assertThat(use.input().get("orderId")).isEqualTo(new java.math.BigDecimal("930003"));
        var captured=ArgumentCaptor.forClass(ConverseRequest.class);
        verify(client).converse(captured.capture());
        var request=captured.getValue();
        assertThat(request.messages().get(1).content().getLast().toolUse().toolUseId()).isEqualTo("first-id");
        var sentResult=request.messages().getLast().content().getFirst().toolResult();
        assertThat(sentResult.toolUseId()).isEqualTo("first-id");
        assertThat(sentResult.status()).isEqualTo(ToolResultStatus.ERROR);
        assertThat(sentResult.content().getFirst().json().asMap().get("missing").isNull()).isTrue();
        assertThat(sentResult.content().getFirst().json().asMap().get("ok").asBoolean()).isFalse();
        assertThat(request.toolConfig().tools()).extracting(tool->tool.toolSpec().name())
                .containsExactly("find_my_orders","get_order_support_context");
        assertThat(request.toolConfig().tools().getLast().toolSpec().inputSchema().json().asMap()
                .get("required").asList().getFirst().asString()).isEqualTo("orderId");
    }

    @Test void incompleteAndUnsupportedProviderBlocksFailClosedWhileKeepingKnownUsage() {
        var usage=TokenUsage.builder().inputTokens(8).outputTokens(2).totalTokens(10).build();
        var incomplete=BedrockSupportModel.neutralResponse(ConverseResponse.builder().stopReason(StopReason.MAX_TOKENS)
                .usage(usage).output(ConverseOutput.fromMessage(Message.builder().role(ConversationRole.ASSISTANT)
                        .content(ContentBlock.fromText("중단")).build())).build());
        assertThat(incomplete.outcome()).isEqualTo(SupportModel.Outcome.INCOMPLETE);
        for(var message:List.of(
                Message.builder().role(ConversationRole.ASSISTANT).content(ContentBlock.builder().build()).build(),
                Message.builder().role(ConversationRole.USER).content(ContentBlock.fromText("wrong role")).build(),
                Message.builder().role(ConversationRole.ASSISTANT).content(ContentBlock.fromToolUse(
                        ToolUseBlock.builder().name("find_my_orders").toolUseId("").build())).build())) {
            var response=BedrockSupportModel.neutralResponse(ConverseResponse.builder().stopReason(StopReason.END_TURN)
                    .usage(usage).output(ConverseOutput.fromMessage(message)).build());
            assertThat(response.message()).isNull();
            assertThat(response.usage()).isEqualTo(new SupportModel.Usage(8,2));
        }
    }
}
