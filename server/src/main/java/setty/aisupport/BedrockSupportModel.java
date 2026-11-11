package setty.aisupport;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolChoice;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultStatus;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** AWS types and JSON Document conversion are confined to the Bedrock adapter. */
@Component
@ConditionalOnProperty(
        name = "setty.ai-support.provider",
        havingValue = "bedrock",
        matchIfMissing = true)
@RequiredArgsConstructor
class BedrockSupportModel implements SupportModel {
    private final BedrockRuntimeClient client;
    private final SupportConfiguration.Settings settings;

    public Response converse(
            List<Message> messages, String system, List<Tool> tools, Duration remaining) {
        return invoke(messages, system, tools, remaining, false);
    }

    @Override
    public Response plan(
            List<Message> messages, String system, Map<String, Object> schema, Duration remaining) {
        return invoke(
                messages,
                system,
                List.of(new Tool("support_plan", "Return one support plan", schema)),
                remaining,
                true);
    }

    private Response invoke(
            List<Message> messages,
            String system,
            List<Tool> tools,
            Duration remaining,
            boolean planning) {
        if (!settings.enabled())
            throw new SupportException(503, "AI_DISABLED", "AI 상담 연결이 아직 준비되지 않았습니다.");
        Duration attempt = remaining.minusMillis(100);
        if (attempt.isNegative() || attempt.isZero())
            throw new SupportException(504, "PROCESSING_EXPIRED", "응답 시간이 초과되었습니다.");
        var builder =
                ConverseRequest.builder()
                        .modelId(settings.modelId())
                        .messages(messages.stream().map(BedrockSupportModel::awsMessage).toList())
                        .system(SystemContentBlock.fromText(system));
        if (!tools.isEmpty()) {
            var config = ToolConfiguration.builder()
                    .tools(tools.stream().map(BedrockSupportModel::awsTool).toList());
            if (planning)
                config.toolChoice(
                        ToolChoice.builder()
                                .tool(t -> t.name("support_plan"))
                                .build());
            builder.toolConfig(config.build());
        }
        ConverseResponse response =
                client.converse(
                        builder.inferenceConfig(i -> i.maxTokens(1024))
                                .additionalModelRequestFields(
                                        document(Map.of("thinking", Map.of("type", "disabled"))))
                                .overrideConfiguration(
                                        c ->
                                                c.apiCallTimeout(remaining)
                                                        .apiCallAttemptTimeout(attempt))
                                .build());
        return neutralResponse(response);
    }

    private static software.amazon.awssdk.services.bedrockruntime.model.Message awsMessage(
            Message message) {
        return software.amazon.awssdk.services.bedrockruntime.model.Message.builder()
                .role(
                        message.role() == Role.USER
                                ? ConversationRole.USER
                                : ConversationRole.ASSISTANT)
                .content(message.content().stream().map(BedrockSupportModel::awsBlock).toList())
                .build();
    }

    private static software.amazon.awssdk.services.bedrockruntime.model.Tool awsTool(Tool tool) {
        return software.amazon.awssdk.services.bedrockruntime.model.Tool.fromToolSpec(
                ToolSpecification.builder().name(tool.name()).description(tool.description())
                        .inputSchema(ToolInputSchema.fromJson(document(tool.inputSchema()))).build());
    }

    private static ContentBlock awsBlock(Block block) {
        return switch (block) {
            case Text text -> ContentBlock.fromText(text.text());
            case ToolUse use -> ContentBlock.fromToolUse(ToolUseBlock.builder()
                    .toolUseId(use.id()).name(use.name()).input(document(use.input())).build());
            case ToolResult result -> ContentBlock.fromToolResult(ToolResultBlock.builder()
                    .toolUseId(result.id()).status(result.error() ? ToolResultStatus.ERROR : ToolResultStatus.SUCCESS)
                    .content(ToolResultContentBlock.fromJson(document(result.value()))).build());
        };
    }

    static Response neutralResponse(ConverseResponse response) {
        if (response == null) return new Response(null, Outcome.INCOMPLETE, null);
        Usage usage =
                response.usage() == null
                        ? null
                        : new Usage(
                                response.usage().inputTokens() == null
                                        ? 0
                                        : response.usage().inputTokens(),
                                response.usage().outputTokens() == null
                                        ? 0
                                        : response.usage().outputTokens());
        Outcome outcome =
                response.stopReason() == StopReason.END_TURN
                        ? Outcome.END_TURN
                        : response.stopReason() == StopReason.TOOL_USE
                                ? Outcome.TOOL_USE
                                : Outcome.INCOMPLETE;
        if (response.output() == null
                || response.output().message() == null
                || response.output().message().role() != ConversationRole.ASSISTANT)
            return new Response(null, outcome, usage);
        List<Block> content = new ArrayList<>();
        for (ContentBlock block : response.output().message().content()) {
            if (block.text() != null) content.add(new Text(block.text()));
            else if (block.toolUse() != null) {
                var use = block.toolUse();
                if (use.toolUseId() == null
                        || use.toolUseId().isBlank()
                        || use.name() == null
                        || use.name().isBlank()) return new Response(null, outcome, usage);
                Map<String, Object> input = null;
                if (use.input() != null && use.input().isMap()) {
                    input = new LinkedHashMap<>();
                    for (var entry : use.input().asMap().entrySet())
                        input.put(entry.getKey(), jsonValue(entry.getValue()));
                }
                content.add(new ToolUse(use.toolUseId(), use.name(), input));
            } else
                return new Response(
                        null, outcome,
                        usage); // Disabled thinking: unknown blocks are not silently discarded.
        }
        return new Response(new Message(Role.ASSISTANT, content), outcome, usage);
    }

    private static Document document(Object value) {
        if (value == null) return Document.fromNull();
        if (value instanceof String text) return Document.fromString(text);
        if (value instanceof Boolean bool) return Document.fromBoolean(bool);
        if (value instanceof Number number) return Document.fromNumber(number.toString());
        if (value instanceof List<?> list)
            return Document.fromList(list.stream().map(BedrockSupportModel::document).toList());
        if (value instanceof Map<?, ?> map) {
            Map<String, Document> converted = new LinkedHashMap<>();
            map.forEach((key, item) -> converted.put((String) key, document(item)));
            return Document.fromMap(converted);
        }
        throw new IllegalArgumentException("Unsupported JSON value");
    }

    private static Object jsonValue(Document value) {
        if (value == null || value.isNull()) return null;
        if (value.isString()) return value.asString();
        if (value.isBoolean()) return value.asBoolean();
        if (value.isNumber()) return new BigDecimal(value.asNumber().toString());
        if (value.isList())
            return value.asList().stream().map(BedrockSupportModel::jsonValue).toList();
        if (value.isMap()) {
            Map<String, Object> converted = new LinkedHashMap<>();
            value.asMap().forEach((key, item) -> converted.put(key, jsonValue(item)));
            return converted;
        }
        throw new IllegalArgumentException("Unsupported JSON value");
    }
}
