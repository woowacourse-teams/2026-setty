package setty.aisupport;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stateless Responses API adapter. No provider failover, SDK retry, stored response or secret
 * logging.
 */
final class OpenAiSupportModel implements SupportModel, AutoCloseable {
    private static final URI ENDPOINT = URI.create("https://api.openai.com/v1/responses");
    private final SupportConfiguration.Settings settings;
    private final ObjectMapper json;
    private final String apiKey;
    private final URI endpoint;
    private final ScheduledExecutorService deadlines =
            Executors.newSingleThreadScheduledExecutor(
                    r -> {
                        Thread thread = new Thread(r, "support-openai-deadline");
                        thread.setDaemon(true);
                        return thread;
                    });

    OpenAiSupportModel(SupportConfiguration.Settings settings, ObjectMapper json, String apiKey) {
        this(settings, json, apiKey, ENDPOINT);
    }

    // The endpoint is injectable only by package-local tests; deployment cannot redirect a secret
    // to another host.
    OpenAiSupportModel(
            SupportConfiguration.Settings settings,
            ObjectMapper json,
            String apiKey,
            URI endpoint) {
        this.settings = settings;
        this.json = json;
        this.apiKey = apiKey;
        this.endpoint = endpoint;
        if (settings.enabled() && (apiKey == null || apiKey.isBlank()))
            throw new IllegalArgumentException(
                    "OPENAI_API_KEY is required when OpenAI support is enabled");
    }

    @Override
    public Response converse(
            List<Message> messages, String system, List<Tool> tools, Duration remaining) {
        return invoke(request(messages, system, tools), remaining);
    }

    @Override
    public Response plan(
            List<Message> messages, String system, Map<String, Object> schema, Duration remaining) {
        Map<String, Object> body = request(messages, system, List.of());
        body.put("tool_choice", "none");
        body.put(
                "text",
                Map.of(
                        "format",
                        OrderedJson.of(
                                "type",
                                "json_schema",
                                "name",
                                "support_plan",
                                "strict",
                                true,
                                "schema",
                                schema)));
        return invoke(body, remaining);
    }

    private Response invoke(Map<String, Object> request, Duration remaining) {
        if (!settings.enabled())
            throw new SupportException(503, "AI_DISABLED", "AI 상담 연결이 아직 준비되지 않았습니다.");
        long timeout = Math.min(55_000, remaining.toMillis() - 100);
        if (timeout <= 0) throw expired();
        HttpURLConnection connection = null;
        ScheduledFuture<?> timer = null;
        AtomicBoolean expired = new AtomicBoolean();
        try {
            byte[] body = json.writeValueAsBytes(request);
            connection = (HttpURLConnection) endpoint.toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout((int) timeout);
            connection.setReadTimeout((int) timeout);
            connection.setDoOutput(true);
            // Streaming disables HttpURLConnection's automatic POST replay/authentication/redirect
            // handling.
            connection.setFixedLengthStreamingMode(body.length);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            final HttpURLConnection current = connection;
            timer =
                    deadlines.schedule(
                            () -> {
                                expired.set(true);
                                current.disconnect();
                            },
                            timeout,
                            TimeUnit.MILLISECONDS);
            try (var output = connection.getOutputStream()) {
                output.write(body);
            }
            int status = connection.getResponseCode();
            if (status != 200)
                throw new SupportException(
                        502, "MODEL_UNAVAILABLE", "AI 연결에 실패했습니다. 잠시 후 다시 시도해 주세요.");
            byte[] response;
            try (var input = connection.getInputStream()) {
                response = input.readNBytes(1_048_577);
            }
            if (expired.get()) throw expired();
            if (response.length > 1_048_576) return new Response(null, Outcome.INCOMPLETE, null);
            return response(json.readTree(response));
        } catch (SupportException e) {
            if (expired.get()) throw expired();
            throw e;
        } catch (IOException | RuntimeException e) {
            // Provider bodies, request headers and transport exception messages can contain
            // secrets/user text.
            if (expired.get() || e instanceof SocketTimeoutException) throw expired();
            throw new SupportException(502, "MODEL_UNAVAILABLE", "AI 연결에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        } finally {
            if (timer != null) timer.cancel(false);
            if (connection != null) connection.disconnect();
        }
    }

    Map<String, Object> request(List<Message> messages, String system, List<Tool> tools) {
        List<Object> input = new ArrayList<>();
        for (Message message : messages)
            for (Block block : message.content()) {
                if (block instanceof Text text) {
                    input.add(
                            OrderedJson.of(
                                    "role",
                                    message.role() == Role.USER ? "user" : "assistant",
                                    "content",
                                    text.text()));
                } else if (block instanceof ToolUse use) {
                    input.add(
                            OrderedJson.of(
                                    "type",
                                    "function_call",
                                    "call_id",
                                    use.id(),
                                    "name",
                                    use.name(),
                                    "arguments",
                                    json.writeValueAsString(use.input())));
                } else if (block instanceof ToolResult result) {
                    input.add(
                            OrderedJson.of(
                                    "type",
                                    "function_call_output",
                                    "call_id",
                                    result.id(),
                                    "output",
                                    json.writeValueAsString(result.value())));
                }
            }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", settings.modelId());
        request.put("instructions", system);
        request.put("input", input);
        request.put("store", false);
        request.put("stream", false);
        request.put("reasoning", Map.of("effort", "none"));
        request.put("max_output_tokens", 1024);
        request.put("parallel_tool_calls", false);
        request.put(
                "tools",
                tools.stream()
                        .map(
                                tool ->
                                        OrderedJson.of(
                                                "type",
                                                "function",
                                                "name",
                                                tool.name(),
                                                "description",
                                                tool.description(),
                                                "parameters",
                                                strictSchema(tool.inputSchema()),
                                                "strict",
                                                true))
                        .toList());
        return request;
    }

    /** Preserve optional arguments as nullable required properties in OpenAI strict schemas. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> strictSchema(Map<String, Object> source) {
        Map<String, Object> schema = new LinkedHashMap<>(source);
        if ("object".equals(source.get("type"))) {
            Map<String, Object> properties =
                    (Map<String, Object>) source.getOrDefault("properties", Map.of());
            List<String> required = (List<String>) source.getOrDefault("required", List.of());
            Map<String, Object> converted = new LinkedHashMap<>();
            properties.forEach(
                    (name, value) -> {
                        Map<String, Object> child = strictSchema((Map<String, Object>) value);
                        if (!required.contains(name)) {
                            Object type = child.get("type");
                            if (type instanceof String text)
                                child.put("type", List.of(text, "null"));
                            if (child.containsKey("enum")) {
                                List<Object> values =
                                        new ArrayList<>((List<Object>) child.get("enum"));
                                values.add(null);
                                child.put("enum", values);
                            }
                        }
                        converted.put(name, child);
                    });
            schema.put("properties", converted);
            schema.put("required", new ArrayList<>(properties.keySet()));
            schema.put("additionalProperties", false);
        }
        if (source.get("items") instanceof Map<?, ?> items)
            schema.put("items", strictSchema((Map<String, Object>) items));
        return schema;
    }

    Response response(JsonNode root) {
        JsonNode usageNode = root.path("usage");
        Usage usage =
                usageNode.isObject()
                        ? new Usage(
                                usageNode.path("input_tokens").asLong(0),
                                usageNode.path("output_tokens").asLong(0))
                        : null;
        if (!"completed".equals(root.path("status").asString())
                || !root.path("error").isNull() && !root.path("error").isMissingNode())
            return new Response(null, Outcome.INCOMPLETE, usage);
        List<Block> blocks = new ArrayList<>();
        boolean toolUse = false;
        if (!root.path("output").isArray()) return new Response(null, Outcome.INCOMPLETE, usage);
        try {
            for (JsonNode item : root.path("output")) {
                switch (item.path("type").asString()) {
                    case "function_call" -> {
                        String id = item.path("call_id").asString("");
                        String name = item.path("name").asString("");
                        JsonNode arguments = json.readTree(item.path("arguments").asString(""));
                        if (id.isBlank() || name.isBlank() || !arguments.isObject())
                            return new Response(null, Outcome.INCOMPLETE, usage);
                        @SuppressWarnings("unchecked")
                        Map<String, Object> converted = json.convertValue(arguments, Map.class);
                        blocks.add(new ToolUse(id, name, converted));
                        toolUse = true;
                    }
                    case "message" -> {
                        if (!"assistant".equals(item.path("role").asString())
                                || !item.path("content").isArray())
                            return new Response(null, Outcome.INCOMPLETE, usage);
                        for (JsonNode content : item.path("content")) {
                            if (!"output_text".equals(content.path("type").asString())
                                    || !content.path("text").isString())
                                return new Response(
                                        null,
                                        Outcome.INCOMPLETE,
                                        usage); // Refusal/opaque reasoning cannot be replayed by
                                                // this port.
                            blocks.add(new Text(content.path("text").asString()));
                        }
                    }
                    default -> {
                        return new Response(null, Outcome.INCOMPLETE, usage);
                    }
                }
            }
        } catch (RuntimeException e) {
            return new Response(null, Outcome.INCOMPLETE, usage);
        }
        return new Response(
                new Message(Role.ASSISTANT, blocks),
                toolUse ? Outcome.TOOL_USE : Outcome.END_TURN,
                usage);
    }

    private static SupportException expired() {
        return new SupportException(504, "PROCESSING_EXPIRED", "응답 시간이 초과되었습니다.");
    }

    @Override
    public void close() {
        deadlines.shutdownNow();
    }
}
