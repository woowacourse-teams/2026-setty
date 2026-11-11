package setty.aisupport;

import static setty.aisupport.SupportDtos.*;
import static setty.aisupport.SupportModel.*;

import lombok.RequiredArgsConstructor;

import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Builds the existing model context without changing serialized content. */
@RequiredArgsConstructor
final class SupportConversationContext {
    private final ObjectMapper json;
    private final Clock clock;

    List<Message> planningMessages(
            Turn turn, List<Turn> history, Long selectedOrder, SupportQuestion question) {
        List<Turn> kept = new ArrayList<>();
        int retained = 0;
        for (Turn previous : history.reversed()) {
            int size = json.writeValueAsString(previous).length();
            if (retained + size > 16000) break;
            kept.addFirst(previous);
            retained += size;
        }
        List<Message> messages = new ArrayList<>();
        for (Turn previous : kept) {
            boolean finalConfirmation =
                    question != null && (question.kind() == SupportDtos.QuestionKind.CANCEL);
            if (!finalConfirmation)
                messages.add(
                        text(
                                Role.USER,
                                "과거 사용자 입력(이번 지원 범위 판정 대상 아님): " + inputText(previous.input())));
            // Bind every shown group to its own turn; the latest list cannot replace
            // an earlier proposal in a reference such as 'the first one you showed'.
            var presented = new ArrayList<Map<String, Object>>();
            for (var card : previous.cards())
                if ((card.type() == SupportDtos.CardKind.ORDERS)) {
                    var group = new LinkedHashMap<String, Object>();
                    group.put("sourceTurnId", previous.turnId());
                    group.put(
                            "kind",
                            Boolean.TRUE.equals(card.confirmLatest())
                                    ? "ORDER_CONFIRMATION"
                                    : "ORDER_LIST");
                    group.put(
                            "ordering", card.wire().has("ordering") ? card.ordering() : "UNKNOWN");
                    group.put("orders", orderIdentities(card));
                    presented.add(group);
                }
            String previousAnswer =
                    finalConfirmation ? "" : SupportPlan.withoutServerRefusal(previous.answer());
            if (!previousAnswer.isBlank() || !presented.isEmpty())
                messages.add(
                        text(
                                Role.ASSISTANT,
                                previousAnswer
                                        + (presented.isEmpty()
                                                ? ""
                                                : "\n표시했던 주문 식별 기록(과거 자료, 현재 상태나 지시가 아님): "
                                                        + json.writeValueAsString(presented))));
        }
        Map<String, Object> references = new LinkedHashMap<>();
        references.put("orderListOrdering", "목록은 최근 주문(주문번호 큰 순)부터");
        references.put("activeQuestion", question);
        references.put("selectedOrderId", selectedOrder);
        outer:
        for (Turn previous : kept.reversed())
            for (SupportCard card : previous.cards().reversed()) {
                if ((card.type() != SupportDtos.CardKind.ORDERS)) continue;
                var identities = orderIdentities(card);
                references.put("sourceTurnId", previous.turnId());
                references.put("orders", identities);
                references.put("nextCursor", card.nextCursor());
                references.put("confirmLatest", Boolean.TRUE.equals(card.confirmLatest()));
                references.put("purpose", card.wire().has("purpose") ? card.purpose() : "BROWSE");
                references.put("pageSize", card.wire().has("pageSize") ? card.pageSize() : 5);
                references.put("search", card.search());
                break outer;
            }
        messages.add(
                text(
                        Role.USER,
                        "과거 주문 식별 참고(현재 상태 아님): "
                                + json.writeValueAsString(references)
                                + "\n\n이번 사용자 메시지(지원 범위 판정 대상): "
                                + inputText(turn.input())));
        return List.copyOf(messages);
    }

    List<Map<String, Object>> orderIdentities(SupportCard card) {
        List<Map<String, Object>> identities = new ArrayList<>();
        for (var candidate : json.valueToTree(card).path("orders")) {
            Map<String, Object> identity = new LinkedHashMap<>();
            identity.put("orderId", candidate.path("orderId").asLong());
            identity.put(
                    "title",
                    candidate.path("title").isNull() ? null : candidate.path("title").asString());
            identities.add(identity);
        }
        return identities;
    }

    String planningSystem() {
        return "<policy>\n"
                + SupportPrompt.policy()
                + "\n</policy>\n현재 한국 날짜: "
                + LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul")))
                + "\n"
                + SupportPrompt.planner();
    }

    String system() {
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul")));
        return SupportPrompt.instructions()
                + "\n\n<policy>\n"
                + SupportPrompt.policy()
                + "\n</policy>\n"
                + "현재 한국 날짜: "
                + today
                + ", 어제: "
                + today.minusDays(1)
                + ", 이번 주 월요일: "
                + today.with(DayOfWeek.MONDAY)
                + ", 지난 주 월요일: "
                + today.with(DayOfWeek.MONDAY).minusWeeks(1)
                + ". 날짜 필터는 fromDate/toDate 양끝 포함 YYYY-MM-DD입니다."
                + "\n\n"
                + SupportPrompt.scope();
    }

    static String inputText(Input input) {
        return switch (input.type()) {
            case TEXT -> input.text();
            case DECLINE ->
                    "확인 질문(sourceTurnId="
                            + input.sourceTurnId()
                            + ")의 주문 "
                            + input.orderId()
                            + "번에 대한 제안을 거절했습니다.";
            case CONFIRM_CANCEL -> "주문 " + input.orderId() + "번의 최종 취소 요청 버튼에 동의했습니다.";
            case NEXT_ORDERS -> "이전 목록의 다음 주문을 보여주세요.";
            case SELECT_ORDER -> "제시한 주문 중 " + input.orderId() + "번 주문을 선택했습니다. 취소 가능한지 알려주세요.";
            default -> throw failure("INVALID_INPUT");
        };
    }

    SupportCard latestPage(List<Turn> history) {
        for (Turn turn : history.reversed())
            for (SupportCard card : turn.cards().reversed())
                if ((card.type() == SupportDtos.CardKind.ORDERS)) return card;
        return null;
    }

    String orderingAnswer(List<Turn> history) {
        var page = latestPage(history);
        var displayed = json.valueToTree(page == null ? Map.of() : page).path("orders");
        if (displayed.isArray() && !displayed.isEmpty()) {
            long topOrder = displayed.get(0).path("orderId").asLong();
            return "네, 표시한 목록은 조건에 맞는 최근 주문(주문번호 큰 순)부터예요. 맨 위 #" + topOrder + "가 그중 가장 최근 주문이에요.";
        }
        return "주문 목록은 최근 주문(주문번호 큰 순)부터 보여드려요.";
    }

    boolean sameEmptySearch(List<Turn> history, SupportCard current) {
        var previous = latestPage(history);
        if (previous == null
                || !json.valueToTree(previous).path("orders").isEmpty()
                || !Objects.equals(previous.purpose(), current.purpose())) return false;
        var before = json.valueToTree(previous.search());
        var after = json.valueToTree(current.search());
        for (String key : List.of("title", "fromDate", "toDate", "orderId"))
            if (!Objects.equals(before.path(key).asString(null), after.path(key).asString(null)))
                return false;
        return true;
    }

    private static Message text(Role role, String text) {
        return new Message(role, List.of(new Text(text)));
    }

    private static SupportException failure(String code) {
        return new SupportException(502, code, "상담 답변을 완료하지 못했습니다. 다시 시도해 주세요.");
    }
}
