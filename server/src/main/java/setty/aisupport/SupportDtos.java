package setty.aisupport;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class SupportDtos {
    private SupportDtos() {}

    public enum InputKind {
        TEXT,
        SELECT_ORDER,
        DECLINE,
        NEXT_ORDERS,
        CONFIRM_CANCEL,
        INVALID;

        @JsonCreator
        public static InputKind fromJson(String value) {
            if (value == null) return null;
            try {
                return valueOf(value);
            } catch (IllegalArgumentException ignored) {
                return INVALID;
            }
        }
    }

    public enum TurnStatus {
        PROCESSING,
        SUCCEEDED,
        FAILED
    }

    public enum QuestionKind {
        ORDER,
        CANCEL
    }

    public enum CardKind {
        ORDERS,
        ELIGIBILITY,
        CANCELLATION_REQUEST
    }

    public record Input(InputKind type, String text, Long sourceTurnId, Long orderId) {
        void validate() {
            if (type == InputKind.TEXT
                    && text != null
                    && !text.isBlank()
                    && text.length() <= 2000
                    && sourceTurnId == null
                    && orderId == null) return;
            if (type == InputKind.NEXT_ORDERS
                    && text == null
                    && sourceTurnId != null
                    && sourceTurnId > 0
                    && orderId == null) return;
            if ((type == InputKind.SELECT_ORDER
                            || type == InputKind.CONFIRM_CANCEL
                            || type == InputKind.DECLINE)
                    && text == null
                    && sourceTurnId != null
                    && sourceTurnId > 0
                    && orderId != null
                    && orderId > 0) return;
            throw new SupportException(
                    400, "INVALID_INPUT", "질문은 2,000자 이내로 입력하거나 제시된 주문을 선택해 주세요.");
        }
    }

    public record SendRequest(String clientRequestId, Input input) {
        void validate() {
            if (clientRequestId == null
                    || !clientRequestId.matches("[A-Za-z0-9_-]{1,100}")
                    || input == null)
                throw new SupportException(400, "INVALID_INPUT", "요청 식별자와 입력을 확인해 주세요.");
            input.validate();
        }
    }

    public record Conversation(
            long id, String title, Instant createdAt, Instant lastActivityAt, Instant expiresAt) {}

    public record Turn(
            long turnId,
            String clientRequestId,
            Input input,
            TurnStatus status,
            Instant createdAt,
            Instant processingDeadlineAt,
            String answer,
            SupportCardList cards,
            String errorCode) {
        public Turn(
                long turnId,
                String clientRequestId,
                Input input,
                TurnStatus status,
                Instant createdAt,
                Instant processingDeadlineAt,
                String answer,
                List<?> cards,
                String errorCode) {
            this(
                    turnId,
                    clientRequestId,
                    input,
                    status,
                    createdAt,
                    processingDeadlineAt,
                    answer,
                    new SupportCardList(cards),
                    errorCode);
        }
    }

    public record Page<T>(List<T> items, Long nextCursor) {}

    public record ConversationListItem(long id, String title, Instant createdAt, Instant lastActivityAt, Instant expiresAt, boolean hasMessages) {}

    public record ConversationPage(List<ConversationListItem> items, String nextCursor) {}

    public record Detail(
            Conversation conversation, List<Turn> items, Long nextCursor, Turn activeTurn) {}

    public record Result(
            String answer,
            SupportCardList cards,
            long inputTokens,
            long outputTokens,
            List<Map<String, Object>> toolSummary,
            Long selectedOrderId,
            SupportPlan.Action intent) {
        public Result(
                String answer,
                List<?> cards,
                long inputTokens,
                long outputTokens,
                List<Map<String, Object>> toolSummary,
                Long selectedOrderId,
                SupportPlan.Action intent) {
            this(
                    answer,
                    new SupportCardList(cards),
                    inputTokens,
                    outputTokens,
                    toolSummary,
                    selectedOrderId,
                    intent);
        }

        public Result(
                String answer,
                List<?> cards,
                long inputTokens,
                long outputTokens,
                List<Map<String, Object>> toolSummary,
                Long selectedOrderId) {
            this(answer, cards, inputTokens, outputTokens, toolSummary, selectedOrderId, null);
        }

        public Result(
                String answer,
                List<?> cards,
                long inputTokens,
                long outputTokens,
                List<Map<String, Object>> toolSummary) {
            this(answer, cards, inputTokens, outputTokens, toolSummary, null);
        }
    }
}
