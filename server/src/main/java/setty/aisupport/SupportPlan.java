package setty.aisupport;

import static setty.aisupport.SupportModel.*;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.*;

/** Validated plan shared by OpenAI and Bedrock; contains no executable mutations. */
final class SupportPlan {
    enum Action {
        CONFIRM_ORDER_TARGET,
        OUT_OF_SCOPE,
        ANSWER,
        CLARIFY,
        SEARCH_ORDER,
        LATEST_ORDER,
        NEXT_ORDERS,
        GET_ORDER,
        AFFIRM,
        DECLINE,
        ORDERING_INFO,
        UNSUPPORTED_FILTER
    }

    enum Topic {
        NONE(""),
        OTHER_PERSON("다른 사람의 주문 조회"),
        HUMAN_SUPPORT("상담 담당자 연결"),
        GENERAL_KNOWLEDGE("일반 상식 안내"),
        NEWS("뉴스·날씨 같은 실시간 정보 안내"),
        OTHER_SERVICE("다른 서비스 안내"),
        CODING("코드 작성"),
        TRANSLATION("번역"),
        CALCULATION("일반 계산"),
        CREATIVE("창작"),
        SECRET("내부 보안 정보 공개"),
        UNSUPPORTED_ACTION("환불 실행·취소 철회·배송원 연락");
        final String label;

        Topic(String label) {
            this.label = label;
        }
    }

    record Plan(
            Topic unsupportedTopic,
            Action action,
            String answer,
            Long orderId,
            String title,
            String fromDate,
            String toDate,
            Long beforeId,
            String datePreset,
            Integer pageSize,
            String purpose) {
        Plan(
                Topic topic,
                Action action,
                String answer,
                Long orderId,
                String title,
                String from,
                String to,
                Long before,
                String preset) {
            this(topic, action, answer, orderId, title, from, to, before, preset, 5, "BROWSE");
        }
    }

    static Plan parse(Response response, ObjectMapper json) {
        JsonNode node = planNode(response, json);
        validateCoreFields(node);
        validateSearchFields(node);
        validateIntegralFields(node);
        // GET_ORDER resolves an exact order ID; only list lookups apply a cancelled-only filter.
        if (Action.GET_ORDER.name().equals(node.get("action").asString())
                && "CANCELLED_CHECK".equals(node.get("purpose").asString())) {
            ((ObjectNode) node).put("purpose", "CANCEL_CHECK");
        }
        Plan plan = json.convertValue(node, Plan.class);
        validate(plan);
        return plan;
    }

    private static JsonNode planNode(Response response, ObjectMapper json) {
        if (response == null
                || response.message() == null
                || response.message().role() != Role.ASSISTANT
                || response.message().content().size() != 1) throw invalid();
        var block = response.message().content().getFirst();
        return response.outcome() == Outcome.END_TURN && block instanceof Text t
                ? json.readTree(t.text())
                : response.outcome() == Outcome.TOOL_USE
                                && block instanceof ToolUse u
                                && "support_plan".equals(u.name())
                        ? json.valueToTree(u.input())
                        : null;
    }

    private static void validateCoreFields(JsonNode n) {
        if (n == null
                || !n.isObject()
                || n.size() != 11
                || !n.has("unsupportedTopic")
                || !n.has("action")
                || !n.has("answer")
                || !n.has("orderId")
                || !n.has("title")
                || !n.get("unsupportedTopic").isString()
                || !n.get("action").isString()
                || !n.get("answer").isString()
                || !(n.get("orderId").isNull() || n.get("orderId").isNumber())
                || !(n.get("title").isNull() || n.get("title").isString())) throw invalid();
    }

    private static void validateSearchFields(JsonNode n) {
        for (String field : List.of("fromDate", "toDate", "datePreset"))
            if (!n.has(field) || !(n.get(field).isNull() || n.get(field).isString()))
                throw invalid();
        if (!n.has("beforeId") || !(n.get("beforeId").isNull() || n.get("beforeId").isNumber()))
            throw invalid();
        if (!n.has("pageSize")
                || !n.get("pageSize").isNumber()
                || !n.has("purpose")
                || !n.get("purpose").isString()) throw invalid();
    }

    private static void validateIntegralFields(JsonNode n) {
        for (String field : List.of("orderId", "beforeId", "pageSize"))
            if (!n.get(field).isNull()) {
                try {
                    new BigDecimal(n.get(field).asString()).longValueExact();
                } catch (RuntimeException error) {
                    throw invalid();
                }
            }
    }

    static void validate(Plan p) {
        if (p == null
                || p.action() == null
                || p.unsupportedTopic() == null
                || p.answer() == null
                || p.orderId() != null && p.orderId() <= 0
                || p.title() != null && p.title().length() > 200) throw invalid();
        if (p.pageSize() == null
                || p.pageSize() < 1
                || p.pageSize() > 20
                || !Set.of("BROWSE", "CANCEL_CHECK", "CANCELLED_CHECK").contains(p.purpose()))
            throw invalid();
        if ("CANCELLED_CHECK".equals(p.purpose())
                && p.action() != Action.SEARCH_ORDER
                && p.action() != Action.NEXT_ORDERS) throw invalid();
        if (p.beforeId() != null && p.beforeId() <= 0) throw invalid();
        if (p.action() != Action.SEARCH_ORDER
                && (p.fromDate() != null
                        || p.toDate() != null
                        || p.beforeId() != null
                        || p.datePreset() != null)) throw invalid();
        switch (p.action()) {
            case CONFIRM_ORDER_TARGET -> throw invalid();
            case AFFIRM, DECLINE -> {
                if (p.unsupportedTopic() != Topic.NONE
                        || !p.answer().isEmpty()
                        || p.orderId() != null
                        || p.title() != null) throw invalid();
            }
            case OUT_OF_SCOPE -> {
                if (!p.answer().isEmpty()
                        || p.orderId() != null
                        || p.title() != null) throw invalid();
            }
            case ANSWER, CLARIFY -> {
                if (p.answer().isBlank() || p.orderId() != null || p.title() != null)
                    throw invalid();
            }
            case ORDERING_INFO, UNSUPPORTED_FILTER -> {
                if (!p.answer().isEmpty() || p.orderId() != null || p.title() != null)
                    throw invalid();
            }
            case GET_ORDER -> {
                if (!p.answer().isEmpty() || p.orderId() == null || p.title() != null)
                    throw invalid();
            }
            case LATEST_ORDER -> {
                if (!p.answer().isEmpty() || p.orderId() != null) throw invalid();
            }
            case NEXT_ORDERS -> {
                if (!p.answer().isEmpty() || p.orderId() != null || p.title() != null)
                    throw invalid();
            }
            case SEARCH_ORDER -> {
                if (!p.answer().isEmpty()) throw invalid();
            }
        }
    }

    static String finish(Plan p, String supportedAnswer) {
        validate(p);
        String label = p.unsupportedTopic() == Topic.NONE && p.action() == Action.OUT_OF_SCOPE
                ? "기능" : p.unsupportedTopic().label;
        String particle =
                !label.isEmpty() && (label.charAt(label.length() - 1) - 0xAC00) % 28 != 0
                        ? "은"
                        : "는";
        String refusal =
                label.isEmpty()
                        ? ""
                        : "요청하신 "
                                + label
                                + particle
                                + " 이 상담에서 지원하지 않습니다. 본인 주문 찾기와 현재 취소 가능 여부, 취소 정책을 안내해 드릴 수 있어요.";
        if (p.action() == Action.OUT_OF_SCOPE) return refusal;
        return refusal.isEmpty() ? supportedAnswer : refusal + "\n\n" + supportedAnswer;
    }

    private static final List<String> SERVER_REFUSALS =
            Arrays.stream(Topic.values())
                    .map(
                            topic ->
                                    finish(
                                            new Plan(
                                                    topic,
                                                    Action.OUT_OF_SCOPE,
                                                    "",
                                                    null,
                                                    null,
                                                    null,
                                                    null,
                                                    null,
                                                    null),
                                            ""))
                    .toList();

    static String withoutServerRefusal(String answer) {
        if (answer == null) return "";
        String legacyNews = "요청하신 뉴스 안내는 이 상담에서 지원하지 않습니다. 본인 주문 찾기와 현재 취소 가능 여부, 취소 정책을 안내해 드릴 수 있어요.";
        if (answer.equals(legacyNews)) return "";
        if (answer.startsWith(legacyNews + "\n\n")) return answer.substring(legacyNews.length() + 2);
        for (String refusal : SERVER_REFUSALS) {
            if (answer.equals(refusal)) return "";
            if (answer.startsWith(refusal + "\n\n")) return answer.substring(refusal.length() + 2);
        }
        return answer;
    }

    static Map<String, Object> schema() {
        var properties = new LinkedHashMap<String, Object>();
        properties.put(
                "unsupportedTopic",
                OrderedJson.of(
                        "type",
                        "string",
                        "enum",
                        Arrays.stream(Topic.values()).map(Enum::name).toList()));
        properties.put(
                "action",
                OrderedJson.of(
                        "type",
                        "string",
                        "enum",
                        Arrays.stream(Action.values())
                                .filter(action -> action != Action.CONFIRM_ORDER_TARGET)
                                .map(Enum::name)
                                .toList()));
        properties.put("answer", Map.of("type", "string"));
        properties.put("orderId", Map.of("type", List.of("integer", "null")));
        properties.put("title", Map.of("type", List.of("string", "null")));
        for (String field : List.of("fromDate", "toDate", "datePreset"))
            properties.put(field, Map.of("type", List.of("string", "null")));
        properties.put("pageSize", OrderedJson.of("type", "integer", "minimum", 1, "maximum", 20));
        properties.put(
                "purpose",
                OrderedJson.of(
                        "type",
                        "string",
                        "enum",
                        List.of("BROWSE", "CANCEL_CHECK", "CANCELLED_CHECK")));
        properties.put("beforeId", Map.of("type", List.of("integer", "null")));
        return OrderedJson.of(
                "type",
                "object",
                "properties",
                properties,
                "required",
                List.copyOf(properties.keySet()),
                "additionalProperties",
                false);
    }

    private static SupportException invalid() {
        return new SupportException(502, "INVALID_SUPPORT_PLAN", "상담 요청을 이해하지 못했어요. 다시 말씀해 주세요.");
    }
}
