package setty.aisupport;

import static setty.aisupport.SupportDtos.*;
import static setty.aisupport.SupportModel.*;

import org.springframework.stereotype.Component;

import setty.platform.OrderSupportReader;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
class SupportRuntime {
    static final String PROMPT_VERSION = "cancel-v10.9-list-ordinal";
    private final SupportModel model;
    private final OrderSupportReader orders;
    private final ObjectMapper json;
    private final Clock clock;

    private final SupportConversationContext context;
    private final SupportOrderLookup lookup;

    SupportRuntime(SupportModel model, OrderSupportReader orders, ObjectMapper json, Clock clock) {
        this.model = model;
        this.orders = orders;
        this.json = json;
        this.clock = clock;
        this.context = new SupportConversationContext(json, clock);
        this.lookup = new SupportOrderLookup(orders, json, clock);
    }

    Result reconfirm(long member, Turn turn, SupportQuestion question, SupportCard previous) {
        return lookup.reconfirm(member, turn, question, previous);
    }

    Result alternatives(long member, Turn turn, SupportQuestion question) {
        return lookup.alternatives(member, turn, question);
    }

    Result run(long member, Turn turn, List<Turn> history, Long selectedOrder) {
        return run(member, turn, history, selectedOrder, null);
    }

    Result run(
            long member,
            Turn turn,
            List<Turn> history,
            Long selectedOrder,
            SupportCard sourcePage) {
        return run(member, turn, history, selectedOrder, sourcePage, null);
    }

    Result run(
            long member,
            Turn turn,
            List<Turn> history,
            Long selectedOrder,
            SupportCard sourcePage,
            SupportQuestion question) {
        long inputTokens = 0, outputTokens = 0;
        List<SupportCard> cards = new ArrayList<>();
        List<Map<String, Object>> audit = new ArrayList<>();
        try {
            remaining(turn);
            SupportPlan.Plan plan;
            Long excludedOrderId = null;
            if ((turn.input().type() == SupportDtos.InputKind.DECLINE)) {
                plan =
                        new SupportPlan.Plan(
                                SupportPlan.Topic.NONE,
                                SupportPlan.Action.DECLINE,
                                "",
                                null,
                                null,
                                null,
                                null,
                                null,
                                null);
            } else if ((turn.input().type() == SupportDtos.InputKind.SELECT_ORDER)) {
                plan =
                        new SupportPlan.Plan(
                                SupportPlan.Topic.NONE,
                                SupportPlan.Action.GET_ORDER,
                                "",
                                turn.input().orderId(),
                                null,
                                null,
                                null,
                                null,
                                null);
                SupportPlan.validate(plan);
            } else if ((turn.input().type() == SupportDtos.InputKind.NEXT_ORDERS)) {
                if (sourcePage == null) throw failure("INVALID_ORDER_SELECTION");
                plan =
                        new SupportPlan.Plan(
                                SupportPlan.Topic.NONE,
                                SupportPlan.Action.NEXT_ORDERS,
                                "",
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                sourcePage.pageSize() == null ? 5 : sourcePage.pageSize(),
                                "BROWSE");
            } else {
                Response response =
                        model.plan(
                                context.planningMessages(turn, history, selectedOrder, question),
                                context.planningSystem(),
                                SupportPlan.schema(),
                                remaining(turn));
                if (response != null && response.usage() != null) {
                    inputTokens += response.usage().inputTokens();
                    outputTokens += response.usage().outputTokens();
                }
                plan = SupportPlan.parse(response, json);
                audit.add(
                        OrderedJson.of(
                                "name",
                                "plan",
                                "action",
                                plan.action().name(),
                                "unsupportedTopic",
                                plan.unsupportedTopic().name()));
            }
            remaining(turn);
            if (plan.action() == SupportPlan.Action.ORDERING_INFO
                    || plan.action() == SupportPlan.Action.UNSUPPORTED_FILTER) {
                return answerListCapability(
                        plan, history, selectedOrder, inputTokens, outputTokens, audit);
            }
            if (plan.action() == SupportPlan.Action.NEXT_ORDERS) {
                SupportCard page = sourcePage == null ? context.latestPage(history) : sourcePage;
                if (page == null)
                    return new Result(
                            "먼저 어떤 주문을 찾을지 말씀해 주세요.",
                            List.of(),
                            inputTokens,
                            outputTokens,
                            List.copyOf(audit),
                            selectedOrder);
                if ("CANCEL_CHECK".equals(page.purpose())
                        && !"ORDER_ID_DESC".equals(page.ordering()))
                    return new Result(
                            SupportPlan.finish(
                                    plan, "주문 목록의 정렬 기준이 바뀌었어요. 같은 상품명·날짜 조건으로 새로 검색해 주세요."),
                            List.of(),
                            inputTokens,
                            outputTokens,
                            appendAudit(
                                    audit,
                                    OrderedJson.of(
                                            "name",
                                            "legacy_order_cursor",
                                            "outcome",
                                            "RESTART_SEARCH")),
                            selectedOrder);
                if (page.nextCursor() == null)
                    return new Result(
                            SupportPlan.finish(plan, "모든 주문을 확인했어요. 더 표시할 주문이 없어요."),
                            List.of(),
                            inputTokens,
                            outputTokens,
                            List.copyOf(audit),
                            selectedOrder);
                var search = json.valueToTree(page.wire().has("search") ? page.search() : Map.of());
                excludedOrderId =
                        search.path("excludedOrderId").isNumber()
                                ? search.path("excludedOrderId").asLong()
                                : null;
                plan =
                        new SupportPlan.Plan(
                                plan.unsupportedTopic(),
                                SupportPlan.Action.SEARCH_ORDER,
                                "",
                                search.path("orderId").isNumber()
                                        ? search.path("orderId").asLong()
                                        : null,
                                nullableString(search, "title"),
                                nullableString(search, "fromDate"),
                                nullableString(search, "toDate"),
                                ((Number) page.nextCursor()).longValue(),
                                null,
                                plan.pageSize(),
                                page.wire().has("purpose") ? page.purpose() : "BROWSE");
            }
            if (plan.action() != SupportPlan.Action.SEARCH_ORDER
                    && plan.action() != SupportPlan.Action.LATEST_ORDER
                    && plan.action() != SupportPlan.Action.GET_ORDER)
                return new Result(
                        SupportPlan.finish(plan, plan.answer()),
                        List.of(),
                        inputTokens,
                        outputTokens,
                        List.copyOf(audit),
                        selectedOrder,
                        plan.action());

            if (!orders.isActiveMember(member))
                throw new SupportException(401, "INVALID_TOKEN", "로그인이 필요합니다.");
            // A model-resolved identifier is only a candidate. It cannot establish a
            // cancellation target, even when it names an owned and eligible order.
            if (plan.action() == SupportPlan.Action.GET_ORDER
                    && (turn.input().type() == SupportDtos.InputKind.TEXT)) {
                Result target =
                        lookup.confirmTarget(member, plan, history, inputTokens, outputTokens);
                audit.addAll(target.toolSummary());
                return new Result(
                        target.answer(),
                        target.cards(),
                        inputTokens,
                        outputTokens,
                        List.copyOf(audit),
                        target.selectedOrderId(),
                        target.intent());
            }
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("orderId", plan.orderId());
            if (plan.action() == SupportPlan.Action.LATEST_ORDER) args.put("title", plan.title());
            String name =
                    plan.action() == SupportPlan.Action.LATEST_ORDER
                            ? "find_latest_my_order"
                            : plan.action() == SupportPlan.Action.SEARCH_ORDER
                                    ? "find_my_orders"
                                    : "get_order_support_context";
            if (plan.action() == SupportPlan.Action.SEARCH_ORDER) {
                args.put("title", plan.title());
                args.put("fromDate", plan.fromDate());
                args.put("toDate", plan.toDate());
                args.put("beforeId", plan.beforeId());
                args.put("datePreset", plan.datePreset());
                args.put("pageSize", plan.pageSize());
                args.put("cancellationTargets", "CANCEL_CHECK".equals(plan.purpose()));
                args.put("excludedOrderId", excludedOrderId);
                args.put("cancelledOnly", "CANCELLED_CHECK".equals(plan.purpose()));
            }
            SupportCard card;
            try {
                SupportCard value =
                        lookup.execute(member, new ToolUse("server-plan", name, args), cards);
                card = value;
                if (plan.action() == SupportPlan.Action.SEARCH_ORDER) {
                    card = SupportCardCodec.with(card, "purpose", plan.purpose());
                    cards.set(cards.size() - 1, card);
                }
                audit.add(OrderedJson.of("name", name, "outcome", "SUCCESS"));
            } catch (RuntimeException error) {
                String code = error instanceof SupportException e ? e.code : "LOOKUP_FAILED";
                audit.add(OrderedJson.of("name", name, "outcome", code));
                throw error instanceof SupportException e ? e : failure("LOOKUP_FAILED");
            }
            remaining(turn);
            if (plan.action() == SupportPlan.Action.SEARCH_ORDER
                    || plan.action() == SupportPlan.Action.LATEST_ORDER) {
                return answerOrderSearch(
                        member, plan, history, card, cards, audit, inputTokens, outputTokens);
            }
            return new Result(
                    SupportPlan.finish(plan, contextAnswer(card)),
                    List.copyOf(cards),
                    inputTokens,
                    outputTokens,
                    List.copyOf(audit),
                    plan.orderId());
        } catch (RuntimeException error) {
            SupportException failure =
                    error instanceof SupportException known
                            ? known
                            : failure("INVALID_MODEL_RESPONSE");
            failure.evidence =
                    new Result(null, List.of(), inputTokens, outputTokens, List.copyOf(audit));
            throw failure;
        }
    }

    private Result answerListCapability(
            SupportPlan.Plan plan,
            List<Turn> history,
            Long selectedOrder,
            long inputTokens,
            long outputTokens,
            List<Map<String, Object>> audit) {
        String answer =
                plan.action() == SupportPlan.Action.ORDERING_INFO
                        ? context.orderingAnswer(history)
                        : "요청하신 정렬·필터는 이 상담에서 지원하지 않아요. 기본 정렬은 최근 주문(주문번호 큰 순)입니다. 주문 내역"
                                + " 화면에서 주문을 확인해 주세요.";
        return new Result(
                SupportPlan.finish(plan, answer),
                List.of(),
                inputTokens,
                outputTokens,
                appendAudit(
                        audit,
                        OrderedJson.of("name", "order_list_capability", "outcome", plan.action().name())),
                selectedOrder,
                plan.action());
    }

    private Result answerOrderSearch(
            long member,
            SupportPlan.Plan plan,
            List<Turn> history,
            SupportCard card,
            List<SupportCard> cards,
            List<Map<String, Object>> audit,
            long inputTokens,
            long outputTokens) {
        OrderSupportReader.SearchResult found =
                new OrderSupportReader.SearchResult(
                        ((List<SupportPresentation.OrderView>) card.orders())
                                .stream()
                                        .map(
                                                v ->
                                                        new OrderSupportReader.Candidate(
                                                                v.orderId(),
                                                                v.title(),
                                                                v.createdAt(),
                                                                v.orderStatus(),
                                                                v.thumbnailUrl(),
                                                                v.totalPrice(),
                                                                v.deliveryStatus()))
                                        .toList(),
                        (Long) card.nextCursor(),
                        Boolean.TRUE.equals(card.hasUndatedOrders()),
                        (Boolean.TRUE.equals(card.hasUndatedOrders()) ? 1 : 0));
        if (found.orders().isEmpty()) {
            @SuppressWarnings("unchecked")
            var search = (Map<String, Object>) card.search();
            String description = SupportPresentation.searchDescription(search, plan.purpose());
            if (plan.beforeId() == null && context.sameEmptySearch(history, card)) {
                var fallbackArgs = new LinkedHashMap<String, Object>();
                fallbackArgs.put("pageSize", 5);
                var recent =
                        lookup.execute(
                                member,
                                new ToolUse(
                                        "empty-search-fallback", "find_my_orders", fallbackArgs),
                                cards);
                recent = SupportCardCodec.with(recent, "purpose", "BROWSE");
                cards.set(cards.size() - 1, recent);
                audit.add(OrderedJson.of("name", "empty_search_fallback", "outcome", "RECENT_ORDERS"));
                boolean empty = ((List<?>) recent.orders()).isEmpty();
                return new Result(
                        SupportPlan.finish(
                                plan,
                                description
                                        + " 조건으로는 다시 찾지 못했어요. "
                                        + (empty
                                                ? "최근 주문도 찾지 못했어요. 주문 내역 화면을 확인하거나 상품명·날짜"
                                                        + " 조건을 바꿔 주세요."
                                                : "조건을 넓혀 최근 주문을 보여드릴게요.")),
                        List.copyOf(cards),
                        inputTokens,
                        outputTokens,
                        List.copyOf(audit),
                        null);
            }
            return new Result(
                    SupportPlan.finish(plan, description + " 조건으로 찾은 주문이 없어요. 최근 주문을 보여드릴까요?"),
                    List.copyOf(cards),
                    inputTokens,
                    outputTokens,
                    List.copyOf(audit),
                    null);
        }
        return new Result(
                SupportPlan.finish(
                        plan,
                        plan.action() == SupportPlan.Action.LATEST_ORDER
                                ? SupportPresentation.renderLatest(found)
                                : SupportPresentation.renderOrders(
                                        found, "CANCELLED_CHECK".equals(plan.purpose()))),
                List.copyOf(cards),
                inputTokens,
                outputTokens,
                List.copyOf(audit),
                null);
    }

    private List<Map<String, Object>> appendAudit(
            List<Map<String, Object>> audit, Map<String, Object> entry) {
        audit.add(entry);
        return List.copyOf(audit);
    }

    private String nullableString(JsonNode node, String key) {
        return node.path(key).isString() ? node.path(key).asString() : null;
    }

    private Duration remaining(Turn turn) {
        Duration remaining = Duration.between(clock.instant(), turn.processingDeadlineAt());
        if (remaining.toMillis() <= 100) throw failure("PROCESSING_EXPIRED");
        return remaining;
    }

    private String contextAnswer(SupportCard card) {
        return switch (((EligibilityCard) card).eligibility()) {
            case "ELIGIBLE" -> "이 주문은 지금 취소 요청할 수 있어요. 아래에서 취소 여부를 선택해 주세요.";
            case "INELIGIBLE" -> "이 주문은 지금 취소 요청을 할 수 없어요. 이유는 아래에서 확인해 주세요.";
            default -> "주문 상태를 확인하지 못했어요. 잠시 뒤 다시 확인해 주세요.";
        };
    }

    Result inspect(long member, Turn turn, long order) {
        var selection =
                new Turn(
                        turn.turnId(),
                        turn.clientRequestId(),
                        new Input(SupportDtos.InputKind.SELECT_ORDER, null, turn.turnId(), order),
                        turn.status(),
                        turn.createdAt(),
                        turn.processingDeadlineAt(),
                        null,
                        List.of(),
                        null);
        return run(member, selection, List.of(), order);
    }

    static SupportCard contextCard(OrderSupportReader.Context context) {
        return SupportCardCodec.context(context);
    }

    static List<Tool> tools() {
        return List.of(
                tool(
                        "find_my_orders",
                        "인증 구매자 주문을 최대5개 조회. 상품명/주문번호/한국 생성일 범위/다음 페이지. 날짜 없는 옛 주문 주의.",
                        OrderedJson.of(
                                "orderId",
                                Map.of("type", "integer"),
                                "title",
                                Map.of("type", "string"),
                                "fromDate",
                                Map.of("type", "string"),
                                "toDate",
                                Map.of("type", "string"),
                                "beforeId",
                                Map.of("type", "integer"),
                                "datePreset",
                                OrderedJson.of(
                                        "type",
                                        "string",
                                        "enum",
                                        List.of("TODAY", "YESTERDAY", "THIS_WEEK", "LAST_WEEK"))),
                        List.of()),
                tool(
                        "get_order_support_context",
                        "본인 주문의 현재 실제 배송 상태와 서버 취소 가능 여부/사유 조회.",
                        Map.of("orderId", Map.of("type", "integer")),
                        List.of("orderId")));
    }

    private static Tool tool(
            String name, String description, Map<String, ?> properties, List<String> required) {
        return new Tool(
                name,
                description,
                OrderedJson.of(
                        "type",
                        "object",
                        "properties",
                        properties,
                        "required",
                        required,
                        "additionalProperties",
                        false));
    }

    private static String safeName(String name) {
        return Set.of("find_my_orders", "get_order_support_context").contains(name)
                ? name
                : "UNKNOWN";
    }

    private static Message text(Role role, String text) {
        return new Message(role, List.of(new Text(text)));
    }

    private static SupportException failure(String code) {
        return new SupportException(502, code, "상담 답변을 완료하지 못했습니다. 다시 시도해 주세요.");
    }
}
