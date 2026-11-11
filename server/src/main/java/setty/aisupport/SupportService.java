package setty.aisupport;

import static setty.aisupport.SupportDtos.*;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
class SupportService {
    private final SupportStore store;
    private final SupportRuntime runtime;
    private final SupportConfiguration.Settings settings;
    private final Semaphore slots;
    private final SupportCancellationService cancellation;
    private final SupportCancellationResults results;

    @Autowired
    SupportService(
            SupportStore store,
            SupportRuntime runtime,
            SupportConfiguration.Settings settings,
            SupportCancellationService cancellation,
            SupportCancellationResults results) {
        this.store = store;
        this.runtime = runtime;
        this.settings = settings;
        this.slots = new Semaphore(settings.maxConcurrent());
        this.cancellation = cancellation;
        this.results = results;
    }

    Turn send(long member, long conversation, SendRequest request) {
        try {
            return sendInternal(member, conversation, request);
        } catch (SupportException error) {
            log.info("support_gate conversation={} outcome={}", conversation, error.code);
            throw error;
        }
    }

    private Turn sendInternal(long member, long conversation, SendRequest request) {
        request.validate();
        Turn existing = store.existing(member, conversation, request);
        if (existing != null) return existing;
        if (!settings.enabled())
            throw new SupportException(
                    503, "AI_DISABLED", "AI 상담 연결이 아직 준비되지 않았습니다. 이전 대화는 확인할 수 있습니다.");
        if ((request.input().type() == SupportDtos.InputKind.CONFIRM_CANCEL))
            return cancellation.confirm(member, conversation, request);
        if (!acquireSlot())
            throw new SupportException(429, "AI_BUSY", "상담 요청이 많습니다. 잠시 후 다시 시도해 주세요.");
        try {
            SupportStore.Acceptance accepted = store.accept(member, conversation, request);
            if (!accepted.created()) return accepted.turn();
            long start = System.nanoTime();
            Result result = null;
            String error = null;
            FinalConsentRule.Decision consentDecision = null;
            try {
                result =
                        (request.input().type() == SupportDtos.InputKind.NEXT_ORDERS)
                                ? runtime.run(
                                        member,
                                        accepted.turn(),
                                        store.history(member, conversation),
                                        store.selected(member, conversation),
                                        store.orderPage(
                                                member,
                                                conversation,
                                                request.input().sourceTurnId()))
                                : runtime.run(
                                        member,
                                        accepted.turn(),
                                        store.history(member, conversation),
                                        store.selected(member, conversation),
                                        null,
                                        accepted.question());
                SupportQuestion question = accepted.question();
                log.info(
                        "support_decision conversation={} turn={} question={} kind={} intent={}",
                        conversation,
                        accepted.turn().turnId(),
                        question == null ? null : question.sourceTurnId(),
                        question == null ? null : question.kind(),
                        result.intent());
                if ((result.intent() == SupportPlan.Action.AFFIRM)) {
                    if (question == null)
                        result =
                                new Result(
                                        "먼저 취소하려는 주문을 알려주세요.",
                                        List.of(),
                                        result.inputTokens(),
                                        result.outputTokens(),
                                        result.toolSummary());
                    else if ((question.kind() == SupportDtos.QuestionKind.CANCEL)
                            && store.isFresh(question)) {
                        consentDecision = FinalConsentRule.evaluate(request.input().text());
                        if (consentDecision.outcome() == FinalConsentRule.Outcome.EXPLICIT) {
                            return cancellation.confirmAccepted(
                                    member,
                                    conversation,
                                    accepted.turn(),
                                    question,
                                    withConsentAudit(result, consentDecision),
                                    settings.modelId(),
                                    Duration.ofNanos(System.nanoTime() - start).toMillis());
                        }
                        result =
                                reconfirmAmbiguousConsent(
                                        member, conversation, accepted.turn(), question, result);
                    } else
                        result =
                                combine(
                                        result,
                                        runtime.inspect(
                                                member, accepted.turn(), question.orderId()));
                } else if ((result.intent() == SupportPlan.Action.DECLINE)) {
                    result = answerDecline(member, accepted.turn(), question, result);
                } else if (question != null
                        && (question.kind() == SupportDtos.QuestionKind.CANCEL)
                        && ((result.intent() == SupportPlan.Action.ANSWER)
                                || (result.intent() == SupportPlan.Action.CLARIFY))) {
                    result =
                            answerCancellationFollowup(
                                    member, conversation, accepted.turn(), question, result);
                }
                result = withPreviousRequest(member, result);
            } catch (SupportException e) {
                error = e.code;
                result = e.evidence;
            } catch (RuntimeException e) {
                // Provider errors can contain submitted prompts. Never log exception messages or
                // raw request/response bodies.
                error = "MODEL_UNAVAILABLE";
            }
            if (consentDecision != null) result = withConsentAudit(result, consentDecision);
            return store.complete(
                    member,
                    conversation,
                    accepted.turn().turnId(),
                    result,
                    error,
                    settings.modelId(),
                    Duration.ofNanos(System.nanoTime() - start).toMillis());
        } finally {
            slots.release();
        }
    }

    private boolean acquireSlot() {
        try {
            return slots.tryAcquire(settings.slotWaitMs(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private Result reconfirmAmbiguousConsent(
            long member,
            long conversation,
            Turn turn,
            SupportQuestion question,
            Result interpretation) {
        Result refreshed =
                combine(
                        interpretation,
                        runtime.reconfirm(
                                member,
                                turn,
                                question,
                                store.confirmationFacts(member, conversation, question)));
        boolean canOffer =
                refreshed.cards().stream()
                        .anyMatch(card -> Boolean.TRUE.equals(card.cancellationOffer()));
        String answer =
                canOffer ? "안전을 위해 최종 취소는 아래 '취소 요청' 버튼으로 한 번 더 확인해 주세요." : refreshed.answer();
        return new Result(
                answer,
                refreshed.cards(),
                refreshed.inputTokens(),
                refreshed.outputTokens(),
                refreshed.toolSummary(),
                question.orderId());
    }

    private Result answerDecline(
            long member, Turn turn, SupportQuestion question, Result interpretation) {
        return question != null && (question.kind() == SupportDtos.QuestionKind.ORDER)
                ? combine(interpretation, runtime.alternatives(member, turn, question))
                : new Result(
                        "취소 요청을 진행하지 않았어요. 다른 주문을 찾으려면 상품명을 알려주세요.",
                        List.of(),
                        interpretation.inputTokens(),
                        interpretation.outputTokens(),
                        interpretation.toolSummary());
    }

    private Result answerCancellationFollowup(
            long member,
            long conversation,
            Turn turn,
            SupportQuestion question,
            Result interpretation) {
        Result refreshed =
                runtime.reconfirm(
                        member,
                        turn,
                        question,
                        store.confirmationFacts(member, conversation, question));
        return new Result(
                interpretation.answer() + "\n\n" + refreshed.answer(),
                refreshed.cards(),
                interpretation.inputTokens() + refreshed.inputTokens(),
                interpretation.outputTokens() + refreshed.outputTokens(),
                combine(interpretation, refreshed).toolSummary(),
                question.orderId());
    }

    private Result withConsentAudit(Result result, FinalConsentRule.Decision decision) {
        Result original = result == null ? new Result("", List.of(), 0, 0, List.of()) : result;
        var audit = new ArrayList<Map<String, Object>>(original.toolSummary());
        audit.add(
                OrderedJson.of(
                        "name",
                        "final_consent_rule",
                        "outcome",
                        decision.outcome().name(),
                        "version",
                        decision.version()));
        return new Result(
                original.answer(),
                original.cards(),
                original.inputTokens(),
                original.outputTokens(),
                audit,
                original.selectedOrderId(),
                original.intent());
    }

    private Result withPreviousRequest(long member, Result result) {
        if (result == null || result.selectedOrderId() == null) return result;
        var eligibility =
                result.cards().stream()
                        .filter(c -> (c.type() == SupportDtos.CardKind.ELIGIBILITY))
                        .findFirst();
        if (eligibility.isEmpty()) return result;
        var previous = results.latest(member, result.selectedOrderId());
        if (previous.isEmpty()) return result;
        var request = previous.get();
        var cards = new ArrayList<SupportCard>(result.cards());
        cards.add(
                SupportCardCodec.receipt(
                        eligibility.get().order(),
                        request.requestId(),
                        request.acceptedAt(),
                        true,
                        request.status()));
        return new Result(
                result.answer(),
                cards,
                result.inputTokens(),
                result.outputTokens(),
                result.toolSummary(),
                result.selectedOrderId());
    }

    private Result combine(Result interpretation, Result answer) {
        var audit = new ArrayList<Map<String, Object>>(interpretation.toolSummary());
        audit.addAll(answer.toolSummary());
        audit.add(OrderedJson.of("name", "interpret_confirmation", "outcome", interpretation.intent()));
        return new Result(
                answer.answer(),
                answer.cards(),
                interpretation.inputTokens() + answer.inputTokens(),
                interpretation.outputTokens() + answer.outputTokens(),
                audit,
                answer.selectedOrderId());
    }
}
