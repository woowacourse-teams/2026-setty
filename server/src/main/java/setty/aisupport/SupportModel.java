package setty.aisupport;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Provider-independent conversational tool protocol. JSON values never carry SDK types. */
interface SupportModel {
    Response converse(List<Message> messages, String system, List<Tool> tools, Duration remaining);

    /** A plan is data, never an executable domain tool request. Adapters may enforce its shape. */
    default Response plan(
            List<Message> messages, String system, Map<String, Object> schema, Duration remaining) {
        return converse(
                messages,
                system,
                List.of(new Tool("support_plan", "Return one support plan", schema)),
                remaining);
    }

    enum Role {
        USER,
        ASSISTANT
    }

    enum Outcome {
        END_TURN,
        TOOL_USE,
        INCOMPLETE
    }

    sealed interface Block permits Text, ToolUse, ToolResult {}

    record Text(String text) implements Block {}

    record ToolUse(String id, String name, Map<String, Object> input) implements Block {}

    record ToolResult(String id, Object value, boolean error) implements Block {}

    record Message(Role role, List<Block> content) {
        public Message {
            content = content == null ? List.of() : List.copyOf(content);
        }
    }

    record Tool(String name, String description, Map<String, Object> inputSchema) {}

    record Usage(long inputTokens, long outputTokens) {}

    record Response(Message message, Outcome outcome, Usage usage) {}
}
