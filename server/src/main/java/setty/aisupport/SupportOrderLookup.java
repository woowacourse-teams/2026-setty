package setty.aisupport;

import static setty.aisupport.SupportDtos.*;
import static setty.aisupport.SupportModel.*;

import lombok.RequiredArgsConstructor;

import setty.platform.OrderSupportReader;

import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Authenticated order lookup and confirmation facts; no model calls or mutations. */
@RequiredArgsConstructor
final class SupportOrderLookup {
    private final OrderSupportReader orders;
    private final ObjectMapper json;
    private final Clock clock;

    SupportCard execute(long member, ToolUse use, List<SupportCard> cards) {
        if (use.input() == null) throw failure("INVALID_TOOL_INPUT");
        Map<String, Object> args = use.input();
        if ("find_my_orders".equals(use.name())) {
            if (!Set.of(
                            "orderId",
                            "title",
                            "fromDate",
                            "toDate",
                            "beforeId",
                            "datePreset",
                            "pageSize",
                            "cancellationTargets",
                            "excludedOrderId",
                            "cancelledOnly")
                    .containsAll(args.keySet())) throw failure("INVALID_TOOL_INPUT");
            String title = string(args, "title");
            if (title != null && title.length() > 200) throw failure("INVALID_TOOL_INPUT");
            LocalDate from = date(args, "fromDate");
            LocalDate to = date(args, "toDate");
            String preset = string(args, "datePreset");
            if (preset != null) {
                if (from != null || to != null) throw failure("INVALID_TOOL_INPUT");
                LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul")));
                switch (preset) {
                    case "TODAY" -> {
                        from = today;
                        to = today;
                    }
                    case "YESTERDAY" -> {
                        from = today.minusDays(1);
                        to = from;
                    }
                    case "THIS_WEEK" -> {
                        from = today.with(DayOfWeek.MONDAY);
                        to = from.plusDays(6);
                    }
                    case "LAST_WEEK" -> {
                        from = today.with(DayOfWeek.MONDAY).minusWeeks(1);
                        to = from.plusDays(6);
                    }
                    default -> throw failure("INVALID_TOOL_INPUT");
                }
            }
            OrderSupportReader.Search query =
                    OrderSupportReader.Search.browse(
                                    number(args, "orderId"),
                                    title,
                                    from,
                                    to,
                                    number(args, "beforeId"),
                                    args.get("pageSize") == null
                                            ? 5
                                            : Math.toIntExact(number(args, "pageSize")))
                            .excluding(number(args, "excludedOrderId"));
            if (Boolean.TRUE.equals(args.get("cancellationTargets")))
                query = query.forCancellation();
            if (Boolean.TRUE.equals(args.get("cancelledOnly"))) query = query.onlyCancelled();
            OrderSupportReader.SearchResult found = orders.findMyOrders(member, query);
            Map<String, Object> search = new LinkedHashMap<>(args);
            search.remove("beforeId");
            search.put("fromDate", from == null ? null : from.toString());
            search.put("toDate", to == null ? null : to.toString());
            search.put("datePreset", null);
            var card = SupportCardCodec.orders(found, search, query.pageSize());
            // A new candidate search replaces the prior selection context. Ordinals bind to this
            // displayed group.
            cards.removeIf(
                    existing ->
                            (existing.type() == SupportDtos.CardKind.ELIGIBILITY)
                                    || (existing.type() == SupportDtos.CardKind.ORDERS));
            cards.add(card);
            return card;
        }
        if ("find_latest_my_order".equals(use.name())) {
            if (!Set.of("orderId", "title").equals(args.keySet()) || args.get("orderId") != null)
                throw failure("INVALID_TOOL_INPUT");
            var found =
                    orders.findMyOrders(
                            member, OrderSupportReader.Search.latest(string(args, "title")));
            var card = SupportCardCodec.latest(found, args);
            cards.add(card);
            return card;
        }
        if ("get_order_support_context".equals(use.name())) {
            if (!Set.of("orderId").equals(args.keySet())) throw failure("INVALID_TOOL_INPUT");
            Long id = number(args, "orderId");
            if (id == null) throw failure("INVALID_TOOL_INPUT");
            SupportCard card = contextCard(orders.getContext(member, id));
            cards.add(card);
            return card;
        }
        throw failure("UNKNOWN_TOOL");
    }

    Result confirmTarget(
            long member,
            SupportPlan.Plan plan,
            List<Turn> history,
            long inputTokens,
            long outputTokens) {
        var found =
                orders.findMyOrders(
                        member,
                        OrderSupportReader.Search.browse(
                                plan.orderId(), null, null, null, null, 1));
        // Do not fall back to a different order when the proposed target is unavailable.
        if (found.orders().isEmpty())
            return new Result(
                    "현재 로그인한 계정에서 해당 주문을 찾지 못했어요. 주문번호나 상품명을 다시 알려주세요.",
                    List.of(),
                    inputTokens,
                    outputTokens,
                    List.of(
                            OrderedJson.of(
                                    "name",
                                    "confirm_order_target",
                                    "outcome",
                                    "NOT_FOUND",
                                    "orderId",
                                    plan.orderId())),
                    null);
        if (found.orders().size() != 1
                || !plan.orderId().equals(found.orders().getFirst().orderId()))
            throw failure("INVALID_ORDER_SELECTION");
        if ("CANCELLED".equals(found.orders().getFirst().orderStatus())) {
            var current = orders.getContext(member, plan.orderId());
            if ("ALREADY_CANCELLED".equals(current.reasonCode()))
                return new Result(
                        SupportPlan.finish(plan, "이미 취소된 주문이에요. 이번 대화에서 새 취소 요청을 접수하지 않았어요."),
                        List.of(contextCard(current)),
                        inputTokens,
                        outputTokens,
                        List.of(
                                OrderedJson.of(
                                        "name",
                                        "confirm_order_target",
                                        "outcome",
                                        "ALREADY_CANCELLED")),
                        plan.orderId());
        }
        Map<String, Object> search = new LinkedHashMap<>();
        Long referenceTurn = null;
        outer:
        for (Turn previous : history.reversed())
            for (var card : previous.cards()) {
                if ((card.type() != SupportDtos.CardKind.ORDERS)) continue;
                for (var candidate : json.valueToTree(card).path("orders"))
                    if (candidate.path("orderId").asLong() == plan.orderId()) {
                        referenceTurn = previous.turnId();
                        if (card.search() != null) search.putAll(card.search());
                        break outer;
                    }
            }
        search.remove("orderId");
        search.remove("excludedOrderId");
        search.remove("beforeId");
        var card = SupportCardCodec.target(found, search);
        var audit = new LinkedHashMap<String, Object>();
        audit.put("name", "confirm_order_target");
        audit.put("outcome", "SUCCESS");
        audit.put("orderId", plan.orderId());
        audit.put("referenceTurnId", referenceTurn);
        return new Result(
                SupportPlan.finish(plan, "이 주문을 말씀하시는 건가요? 맞으면 취소 가능 여부를 확인할게요."),
                List.of(card),
                inputTokens,
                outputTokens,
                List.of(audit),
                null,
                SupportPlan.Action.CONFIRM_ORDER_TARGET);
    }

    Result reconfirm(long member, Turn turn, SupportQuestion question, SupportCard previous) {
        remaining(turn);
        if (!orders.isActiveMember(member))
            throw new SupportException(401, "INVALID_TOKEN", "로그인이 필요합니다.");
        Instant observed =
                previous == null || previous.observedAt() == null
                        ? Instant.MIN
                        : previous.observedAt();
        Duration age = Duration.between(observed, clock.instant());
        boolean reuse =
                !age.isNegative()
                        && age.compareTo(Duration.ofMinutes(1)) <= 0
                        && question.fresh(clock.instant())
                        && Boolean.TRUE.equals(previous.cancellationOffer());
        SupportCard card =
                reuse ? previous : contextCard(orders.getContext(member, question.orderId()));
        boolean eligible = Boolean.TRUE.equals(card.cancellationOffer());
        if (eligible) card = SupportCardCodec.with(card, "confirmationOnly", true);
        return new Result(
                eligible ? "이 주문을 취소할까요?" : "주문 상태가 달라져 취소 요청을 진행할 수 없어요. 아래 확인 결과를 봐 주세요.",
                List.of(card),
                0,
                0,
                List.of(
                        OrderedJson.of(
                                "name",
                                "reconfirm_cancellation",
                                "outcome",
                                reuse ? "REUSED_RECENT_FACTS" : "REFRESHED")),
                question.orderId());
    }

    Result alternatives(long member, Turn turn, SupportQuestion question) {
        remaining(turn);
        if (!orders.isActiveMember(member))
            throw new SupportException(401, "INVALID_TOKEN", "로그인이 필요합니다.");
        var args = new LinkedHashMap<String, Object>(question.search());
        args.remove("beforeId");
        args.put("pageSize", 5);
        args.put("cancellationTargets", true);
        args.put("excludedOrderId", question.orderId());
        var cards = new ArrayList<SupportCard>();
        var card =
                execute(member, new ToolUse("server-alternatives", "find_my_orders", args), cards);
        card = SupportCardCodec.with(card, "purpose", "CANCEL_CHECK");
        cards.set(cards.size() - 1, card);
        boolean empty = ((List<?>) card.orders()).isEmpty();
        return new Result(
                empty
                        ? "같은 조건의 다른 주문을 찾지 못했어요. 상품명이나 주문 시기를 알려주세요."
                        : "말씀하신 조건의 다른 주문이에요. 취소하려는 주문을 선택해 주세요.",
                List.copyOf(cards),
                0,
                0,
                List.of(OrderedJson.of("name", "find_alternative_orders", "outcome", "SUCCESS")),
                null);
    }

    static String string(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) return null;
        if (!(value instanceof String)) throw failure("INVALID_TOOL_INPUT");
        return (String) value;
    }

    static LocalDate date(Map<String, Object> args, String key) {
        String value = string(args, key);
        try {
            return value == null ? null : LocalDate.parse(value);
        } catch (RuntimeException e) {
            throw failure("INVALID_TOOL_INPUT");
        }
    }

    static Long number(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) return null;
        if (!(value instanceof Number)) throw failure("INVALID_TOOL_INPUT");
        try {
            long number = new BigDecimal(value.toString()).longValueExact();
            if (number <= 0) throw failure("INVALID_TOOL_INPUT");
            return number;
        } catch (RuntimeException e) {
            throw failure("INVALID_TOOL_INPUT");
        }
    }

    private Duration remaining(Turn turn) {
        Duration remaining = Duration.between(clock.instant(), turn.processingDeadlineAt());
        if (remaining.toMillis() <= 100) throw failure("PROCESSING_EXPIRED");
        return remaining;
    }

    private static SupportException failure(String code) {
        return new SupportException(502, code, "상담 답변을 완료하지 못했습니다. 다시 시도해 주세요.");
    }

    static SupportCard contextCard(OrderSupportReader.Context context) {
        return SupportCardCodec.context(context);
    }
}
