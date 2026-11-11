package setty.aisupport;

import static setty.aisupport.SupportDtos.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import setty.platform.OrderSupportCancellation;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Consent validation, order transition/event and receipt commit together, after model work finishes.
 * TransactionTemplate deliberately reacquires locks for this short transaction; no model call is inside it.
 */
@Service
class SupportCancellationService {
    private final SupportStore store;
    private final OrderSupportCancellation cancellation;
    private final Clock clock;
    private final TransactionTemplate tx;

    SupportCancellationService(
            SupportStore store,
            OrderSupportCancellation cancellation,
            Clock clock,
            PlatformTransactionManager manager) {
        this.store = store;
        this.cancellation = cancellation;
        this.clock = clock;
        tx = new TransactionTemplate(manager);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        tx.setTimeout(20);
    }

    Turn confirm(long member, long conversation, SendRequest request) {
        request.validate();
        if ((request.input().type() != SupportDtos.InputKind.CONFIRM_CANCEL))
            throw new SupportException(400, "INVALID_INPUT", "최종 동의가 필요합니다.");
        return tx.execute(
                status -> {
                    // Locks member -> conversation, including replays from a second browser/request
                    // ID.
                    Turn duplicate = store.existing(member, conversation, request);
                    if (duplicate != null) return duplicate;
                    Turn receipt = store.cancellationReceipt(conversation, request.input());
                    if (receipt != null) return receipt;
                    store.validateCancellationOffer(conversation, request.input());
                    var accepted = store.accept(member, conversation, request);
                    return execute(
                            member,
                            conversation,
                            accepted.turn(),
                            request.input().orderId(),
                            request.input().sourceTurnId(),
                            0,
                            0,
                            "server",
                            0,
                            List.of());
                });
    }

    Turn confirmAccepted(
            long member,
            long conversation,
            Turn turn,
            SupportQuestion question,
            Result interpretation,
            String modelId,
            long durationMs) {
        return tx.execute(
                status -> {
                    // Re-lock after model work; expired/deleted/withdrawn conversations and late
                    // workers cannot execute.
                    Turn current =
                            store.existing(
                                    member,
                                    conversation,
                                    new SendRequest(turn.clientRequestId(), turn.input()));
                    if (current == null || (current.status() != SupportDtos.TurnStatus.PROCESSING))
                        throw new SupportException(
                                409, "CANCELLATION_CONFIRMATION_EXPIRED", "취소 가능 여부를 다시 확인해 주세요.");
                    var consent =
                            new Input(
                                    SupportDtos.InputKind.CONFIRM_CANCEL,
                                    null,
                                    question.sourceTurnId(),
                                    question.orderId());
                    store.validateCancellationOffer(conversation, consent);
                    return execute(
                            member,
                            conversation,
                            turn,
                            question.orderId(),
                            question.sourceTurnId(),
                            interpretation.inputTokens(),
                            interpretation.outputTokens(),
                            modelId,
                            durationMs,
                            interpretation.toolSummary());
                });
    }

    private Turn execute(
            long member,
            long conversation,
            Turn turn,
            long orderId,
            long source,
            long inputTokens,
            long outputTokens,
            String modelId,
            long durationMs,
            List<Map<String, Object>> consentAudit) {
        var outcome = cancellation.request(member, orderId);
        Result result;
        if (outcome.accepted()) {
            store.recordBusinessCancellation(
                    member,
                    orderId,
                    outcome.requestId(),
                    source,
                    turn,
                    outcome.context().policyVersion());
            var card =
                    SupportCardCodec.receipt(
                            SupportPresentation.view(outcome.context().order()),
                            outcome.requestId(),
                            clock.instant(),
                            false,
                            null);
            result =
                    new Result(
                            "취소 요청을 접수했어요. 취소 확정은 배송 상태 확인 후 반영되며, 이 안내는 환불 완료를 뜻하지 않아요. 실제 결제가 없는"
                                + " 테스트 서비스입니다.",
                            List.of(card),
                            inputTokens,
                            outputTokens,
                            List.of(
                                    OrderedJson.of(
                                            "name",
                                            "request_order_cancellation",
                                            "outcome",
                                            "ACCEPTED")),
                            orderId);
        } else {
            var card = SupportRuntime.contextCard(outcome.context());
            result =
                    new Result(
                            "다시 확인한 현재 상태에서는 취소 요청을 접수할 수 없어요. 아래 상태를 확인해 주세요. 이번 요청으로 주문을 변경하지"
                                + " 않았어요.",
                            List.of(card),
                            inputTokens,
                            outputTokens,
                            List.of(
                                    OrderedJson.of(
                                            "name",
                                            "request_order_cancellation",
                                            "outcome",
                                            outcome.context().reasonCode())),
                            orderId);
        }
        var audit = new ArrayList<Map<String, Object>>(consentAudit);
        audit.addAll(result.toolSummary());
        result =
                new Result(
                        result.answer(),
                        result.cards(),
                        result.inputTokens(),
                        result.outputTokens(),
                        audit,
                        result.selectedOrderId());
        store.recordConsent(turn.turnId(), source);
        Turn completed =
                store.complete(
                        member, conversation, turn.turnId(), result, null, modelId, durationMs);
        if ((completed.status() != SupportDtos.TurnStatus.SUCCEEDED))
            throw new IllegalStateException("Cancellation receipt was not persisted");
        return completed;
    }
}
