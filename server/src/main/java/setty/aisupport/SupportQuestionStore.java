package setty.aisupport;

import lombok.RequiredArgsConstructor;

import static setty.aisupport.SupportDtos.*;

import org.springframework.jdbc.core.JdbcTemplate;

import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * Question identity, consent audit and business receipt persistence within the caller transaction.
 */
@RequiredArgsConstructor
final class SupportQuestionStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    SupportQuestion question(long conversationId) {
        String value =
                jdbc.queryForObject(
                        "SELECT active_question_json FROM ai_support_conversation WHERE id=?",
                        String.class,
                        conversationId);
        return value == null ? null : json.readValue(value, SupportQuestion.class);
    }

    boolean isFresh(SupportQuestion question) {
        return question.fresh(clock.instant());
    }

    void recordBusinessCancellation(
            long member,
            long order,
            String requestId,
            long question,
            Turn consent,
            String policyVersion) {
        jdbc.update(
                "INSERT INTO"
                    + " ai_support_cancellation(request_id,member_id,order_id,question_turn_id,consent_turn_id,"
                    + "consent_method,consent_rule_version,policy_version,status,accepted_at)"
                    + " VALUES(?,?,?,?,?,?,?,?,'ACCEPTED',?)",
                requestId,
                member,
                order,
                question,
                consent.turnId(),
                (consent.input().type() == SupportDtos.InputKind.TEXT) ? "TEXT" : "BUTTON",
                (consent.input().type() == SupportDtos.InputKind.TEXT) ? FinalConsentRule.VERSION : null,
                policyVersion,
                ts(clock.instant()));
    }

    void recordConsent(long turnId, long source) {
        jdbc.update(
                "UPDATE ai_support_turn SET consent_source_turn_id=? WHERE id=?", source, turnId);
    }

    void updateQuestion(long conversationId, long turnId, Result result, String error) {
        SupportQuestion next = null;
        if (result != null && error == null)
            for (var card : result.cards()) {
                var node = json.valueToTree(card);
                if ((card.type() == SupportDtos.CardKind.ORDERS)
                        && Boolean.TRUE.equals(card.confirmLatest())
                        && node.path("orders").size() == 1)
                    next =
                            new SupportQuestion(
                                    turnId,
                                    SupportDtos.QuestionKind.ORDER,
                                    node.path("orders").get(0).path("orderId").asLong(),
                                    clock.instant().plus(Duration.ofMinutes(5)),
                                    card.search() == null ? Map.of() : card.search());
                if ((card.type() == SupportDtos.CardKind.ELIGIBILITY)
                        && Boolean.TRUE.equals(card.cancellationOffer()))
                    next =
                            new SupportQuestion(
                                    turnId,
                                    SupportDtos.QuestionKind.CANCEL,
                                    node.path("order").path("orderId").asLong(),
                                    Instant.parse(node.path("confirmationExpiresAt").asString()),
                                    Map.of());
            }
        jdbc.update(
                "UPDATE ai_support_conversation SET active_question_json=? WHERE id=?",
                next == null ? null : json.writeValueAsString(next),
                conversationId);
    }

    private static LocalDateTime ts(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
