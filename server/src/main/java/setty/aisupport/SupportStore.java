package setty.aisupport;

import static setty.aisupport.SupportDtos.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import setty.common.MemberWithdrawn;
import setty.platform.OrderSupportReader;

import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

@Component
class SupportStore {
    private static final Duration RETENTION = Duration.ofDays(30);
    private static final Duration TURN_TIMEOUT = Duration.ofSeconds(60);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final TransactionTemplate acceptanceTx;
    private final ObjectMapper json;
    private final Clock clock;
    private final OrderSupportReader orders;
    private final SupportQuestionStore questions;
    private final SupportRetention retention;
    private final SupportDailyUsage usage;
    private final int monthlyTextLimit;

    SupportStore(
            JdbcTemplate jdbc,
            PlatformTransactionManager manager,
            ObjectMapper json,
            Clock clock,
            OrderSupportReader orders) {
        this(jdbc, manager, json, clock, orders, 50);
    }

    SupportStore(
            JdbcTemplate jdbc,
            PlatformTransactionManager manager,
            ObjectMapper json,
            Clock clock,
            OrderSupportReader orders,
            int dailyTextLimit) {
        this(jdbc, manager, json, clock, orders, dailyTextLimit, 30000);
    }

    @Autowired
    SupportStore(
            JdbcTemplate jdbc,
            PlatformTransactionManager manager,
            ObjectMapper json,
            Clock clock,
            OrderSupportReader orders,
            @Value("${setty.ai-support.daily-text-limit:50}") int dailyTextLimit,
            @Value("${setty.ai-support.monthly-text-limit:30000}") int monthlyTextLimit) {
        if (monthlyTextLimit < 0)
            throw new IllegalArgumentException("Monthly text limit must not be negative");
        this.monthlyTextLimit = monthlyTextLimit;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(manager);
        this.acceptanceTx = new TransactionTemplate(manager);
        // Recovery's range UPDATE must not hold gap locks while another conversation waits
        // for the shared daily counter. Cancellation acceptance already uses READ_COMMITTED.
        this.acceptanceTx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.json = json;
        this.clock = clock;
        this.orders = orders;
        this.questions = new SupportQuestionStore(jdbc, json, clock);
        this.retention = new SupportRetention(jdbc, clock);
        this.usage = new SupportDailyUsage(jdbc, clock, dailyTextLimit);
    }

    Conversation create(long member) {
        return tx.execute(
                status -> {
                    active(member);
                    Instant now = clock.instant();
                    long id =
                            insert(
                                    "INSERT INTO"
                                            + " ai_support_conversation(member_id,title,created_at,"
                                            + "last_activity_at,expires_at)"
                                            + " VALUES(?,?,?,?,?)",
                                    member,
                                    "새 상담",
                                    now,
                                    now,
                                    now.plus(RETENTION));
                    return conversation(member, id, false);
                });
    }

    ConversationPage list(long member, String cursor) {
        return tx.execute(
                status -> {
                    active(member);
                    Instant beforeTime = Instant.parse("9999-01-01T00:00:00Z");
                    long beforeId = Long.MAX_VALUE;
                    if (cursor != null) {
                        try {
                            if (cursor.length() > 200) throw new IllegalArgumentException();
                            String[] decoded =
                                    new String(
                                                    Base64.getUrlDecoder().decode(cursor),
                                                    StandardCharsets.UTF_8)
                                            .split("\\|", -1);
                            if (decoded.length != 2) throw new IllegalArgumentException();
                            beforeTime = Instant.parse(decoded[0]);
                            beforeId = Long.parseLong(decoded[1]);
                            if (beforeId <= 0) throw new IllegalArgumentException();
                        } catch (RuntimeException e) {
                            throw new SupportException(400, "INVALID_CURSOR", "페이지 정보를 확인해 주세요.");
                        }
                    }
                    List<ConversationListItem> found =
                            jdbc.query(
                                    "SELECT c.*, EXISTS (SELECT 1 FROM ai_support_turn t WHERE t.conversation_id=c.id) AS has_messages FROM ai_support_conversation c WHERE member_id=? AND"
                                            + " expires_at>? AND (last_activity_at<? OR"
                                            + " (last_activity_at=? AND id<?)) ORDER BY"
                                            + " last_activity_at DESC,id DESC LIMIT 21",
                                    (rs, row) -> new ConversationListItem(rs.getLong("id"), rs.getString("title"), instant(rs, "created_at"), instant(rs, "last_activity_at"), instant(rs, "expires_at"), rs.getBoolean("has_messages")),
                                    member,
                                    ts(clock.instant()),
                                    ts(beforeTime),
                                    ts(beforeTime),
                                    beforeId);
                    String next = null;
                    if (found.size() > 20) {
                        ConversationListItem last = found.get(19);
                        next =
                                Base64.getUrlEncoder()
                                        .withoutPadding()
                                        .encodeToString(
                                                (last.lastActivityAt() + "|" + last.id())
                                                        .getBytes(StandardCharsets.UTF_8));
                    }
                    return new ConversationPage(found.stream().limit(20).toList(), next);
                });
    }

    Detail detail(long member, long id, Long cursor) {
        return tx.execute(
                status -> {
                    active(member);
                    Conversation c = conversation(member, id, true);
                    recover(id);
                    List<Turn> found =
                            jdbc.query(
                                    "SELECT * FROM ai_support_turn WHERE conversation_id=? AND id<?"
                                            + " ORDER BY id DESC LIMIT 21",
                                    turnMapper(),
                                    id,
                                    cursor == null ? Long.MAX_VALUE : cursor);
                    return new Detail(
                            c,
                            found.stream().limit(20).toList(),
                            found.size() > 20 ? found.get(19).turnId() : null,
                            running(id));
                });
    }

    Turn existing(long member, long id, SendRequest request) {
        return tx.execute(
                status -> {
                    active(member);
                    conversation(member, id, true);
                    recover(id);
                    return duplicate(id, request);
                });
    }

    record Acceptance(Turn turn, boolean created, SupportQuestion question) {}

    Acceptance accept(long member, long id, SendRequest request) {
        return acceptanceTx.execute(
                status -> {
                    active(member);
                    Conversation c = conversation(member, id, true);
                    recover(id);
                    Turn duplicate = duplicate(id, request);
                    if (duplicate != null) return new Acceptance(duplicate, false, question(id));
                    if (running(id) != null)
                        throw new SupportException(409, "TURN_IN_PROGRESS", "이전 질문에 답변하는 중입니다.");
                    if ((request.input().type() == SupportDtos.InputKind.SELECT_ORDER))
                        validateCandidate(id, request.input());
                    if ((request.input().type() == SupportDtos.InputKind.NEXT_ORDERS))
                        orderPage(member, id, request.input().sourceTurnId());
                    if ((request.input().type() == SupportDtos.InputKind.DECLINE)) {
                        var question = question(id);
                        if (question == null
                                || question.sourceTurnId() != request.input().sourceTurnId()
                                || question.orderId() != request.input().orderId())
                            throw new SupportException(
                                    409, "ORDER_CONFIRMATION_EXPIRED", "현재 확인 질문에 답해주세요.");
                    }
                    if (request.input().type() == InputKind.TEXT) {
                        checkMonthlyTextLimit();
                        usage.acceptText(member);
                    }
                    Instant now = clock.instant();
                    long turnId =
                            insert(
                                    "INSERT INTO"
                                        + " ai_support_turn(conversation_id,client_request_id,input_json,"
                                        + "status,created_at,processing_deadline_at)"
                                        + " VALUES(?,?,?,?,?,?)",
                                    id,
                                    request.clientRequestId(),
                                    json.writeValueAsString(request.input()),
                                    TurnStatus.PROCESSING.name(),
                                    now,
                                    now.plus(TURN_TIMEOUT));
                    String title = c.title();
                    if ("새 상담".equals(title)
                            && (request.input().type() == SupportDtos.InputKind.TEXT)) {
                        title = request.input().text().strip();
                        title = title.substring(0, Math.min(80, title.length()));
                    }
                    jdbc.update(
                            "UPDATE ai_support_conversation SET"
                                    + " title=?,last_activity_at=?,expires_at=? WHERE id=?",
                            title,
                            ts(now),
                            ts(now.plus(RETENTION)),
                            id);
                    return new Acceptance(turn(turnId), true, question(id));
                });
    }

    private void checkMonthlyTextLimit() {
        if (monthlyTextLimit == 0) return;
        var today = SupportDailyUsage.today(clock);
        // 잠금 없이 합계를 읽으므로 동시 처리 중인 요청 수만큼 한도를 조금 넘을 수 있다.
        long total = jdbc.queryForObject(
                "SELECT COALESCE(SUM(text_turns),0) FROM ai_support_daily_usage"
                        + " WHERE usage_date BETWEEN ? AND ?",
                Long.class, today.withDayOfMonth(1), today);
        if (total >= monthlyTextLimit)
            throw new SupportException(
                    429,
                    "AI_MONTHLY_LIMIT",
                    "이번 달 상담 사용량을 모두 사용했어요. 버튼 선택과 이전 대화 기록은 계속 이용할 수 있어요.");
    }

    private Turn duplicate(long id, SendRequest request) {
        List<Turn> found =
                jdbc.query(
                        "SELECT * FROM ai_support_turn WHERE conversation_id=? AND"
                                + " client_request_id=?",
                        turnMapper(),
                        id,
                        request.clientRequestId());
        if (found.isEmpty()) return null;
        Turn turn = found.getFirst();
        if (!Objects.equals(turn.input(), request.input()))
            throw new SupportException(409, "REQUEST_ID_CONFLICT", "같은 요청 식별자로 다른 내용을 보낼 수 없습니다.");
        return turn;
    }

    private void validateCandidate(long id, Input input) {
        List<Turn> turns =
                jdbc.query(
                        "SELECT * FROM ai_support_turn WHERE id=? AND conversation_id=? AND"
                                + " status='SUCCEEDED'",
                        turnMapper(),
                        input.sourceTurnId(),
                        id);
        boolean present =
                turns.stream()
                        .flatMap(t -> t.cards().stream())
                        .filter(c -> (c.type() == SupportDtos.CardKind.ORDERS))
                        .flatMap(c -> c.orders().stream())
                        .anyMatch(candidate -> candidate.orderId() == input.orderId().longValue());
        SupportQuestion question = question(id);
        boolean confirmation =
                turns.stream()
                        .flatMap(t -> t.cards().stream())
                        .anyMatch(c -> Boolean.TRUE.equals(c.confirmLatest()));
        if (confirmation
                || question != null && (question.kind() == SupportDtos.QuestionKind.ORDER)) {
            if (question == null
                    || (question.kind() != SupportDtos.QuestionKind.ORDER)
                    || question.sourceTurnId() != input.sourceTurnId()
                    || question.orderId() != input.orderId()
                    || !question.fresh(clock.instant()))
                throw new SupportException(
                        409, "ORDER_CONFIRMATION_EXPIRED", "최근에 제시한 주문을 다시 확인해 주세요.");
        }
        if (!present)
            throw new SupportException(400, "INVALID_ORDER_SELECTION", "이 대화에서 제시한 주문을 선택해 주세요.");
    }

    // Caller holds this conversation's lock in the cancellation transaction.
    Turn cancellationReceipt(long conversationId, Input input) {
        return jdbc
                .query(
                        "SELECT * FROM ai_support_turn WHERE conversation_id=? AND"
                            + " status='SUCCEEDED' AND consent_source_turn_id=? ORDER BY id LIMIT"
                            + " 1",
                        turnMapper(),
                        conversationId,
                        input.sourceTurnId())
                .stream()
                .filter(
                        t ->
                                t.cards().stream()
                                        .anyMatch(
                                                c ->
                                                        json.valueToTree(c)
                                                                        .path("order")
                                                                        .path("orderId")
                                                                        .asLong()
                                                                == input.orderId()))
                .findFirst()
                .orElse(null);
    }

    SupportCard confirmationFacts(long member, long conversationId, SupportQuestion question) {
        return tx.execute(
                status -> {
                    active(member);
                    conversation(member, conversationId, false);
                    return jdbc
                            .query(
                                    "SELECT * FROM ai_support_turn WHERE id=? AND conversation_id=?"
                                            + " AND status='SUCCEEDED'",
                                    turnMapper(),
                                    question.sourceTurnId(),
                                    conversationId)
                            .stream()
                            .flatMap(t -> t.cards().stream())
                            .filter(
                                    c ->
                                            (c.type() == SupportDtos.CardKind.ELIGIBILITY)
                                                    && json.valueToTree(c)
                                                                    .path("order")
                                                                    .path("orderId")
                                                                    .asLong()
                                                            == question.orderId())
                            .findFirst()
                            .orElse(null);
                });
    }

    boolean isFresh(SupportQuestion question) {
        return questions.isFresh(question);
    }

    SupportQuestion question(long conversationId) {
        return questions.question(conversationId);
    }

    void validateCancellationOffer(long conversationId, Input input) {
        SupportQuestion q = question(conversationId);
        if (q == null
                || (q.kind() != SupportDtos.QuestionKind.CANCEL)
                || q.sourceTurnId() != input.sourceTurnId()
                || q.orderId() != input.orderId()
                || !q.fresh(clock.instant()))
            throw new SupportException(
                    409, "CANCELLATION_CONFIRMATION_EXPIRED", "취소 가능 여부를 다시 확인한 후 최종 동의해 주세요.");
        // Keep the actual source fact check, even if the question record was corrupted.
        var source = turn(q.sourceTurnId());
        if (source.cards().stream()
                .noneMatch(
                        c ->
                                (c.type() == SupportDtos.CardKind.ELIGIBILITY)
                                        && "ELIGIBLE".equals(c.eligibility())
                                        && Boolean.TRUE.equals(c.cancellationOffer())
                                        && json.valueToTree(c)
                                                        .path("order")
                                                        .path("orderId")
                                                        .asLong()
                                                == q.orderId()))
            throw new SupportException(
                    409, "CANCELLATION_CONFIRMATION_EXPIRED", "취소 가능 여부를 다시 확인해 주세요.");
    }

    void recordBusinessCancellation(
            long member,
            long order,
            String requestId,
            long question,
            Turn consent,
            String policyVersion) {
        questions.recordBusinessCancellation(
                member, order, requestId, question, consent, policyVersion);
    }

    void recordConsent(long turnId, long source) {
        questions.recordConsent(turnId, source);
    }

    void updateQuestion(long conversationId, long turnId, Result result, String error) {
        questions.updateQuestion(conversationId, turnId, result, error);
    }

    SupportCard orderPage(long member, long conversationId, long sourceTurnId) {
        conversation(member, conversationId, false);
        return jdbc
                .query(
                        "SELECT * FROM ai_support_turn WHERE id=? AND conversation_id=? AND"
                                + " status='SUCCEEDED'",
                        turnMapper(),
                        sourceTurnId,
                        conversationId)
                .stream()
                .flatMap(t -> t.cards().stream())
                .filter(c -> (c.type() == SupportDtos.CardKind.ORDERS))
                .findFirst()
                .orElseThrow(
                        () ->
                                new SupportException(
                                        400,
                                        "INVALID_ORDER_SELECTION",
                                        "이 상담에서 표시한 주문 목록을 선택해 주세요."));
    }

    List<Turn> history(long member, long id) {
        return tx.execute(
                status -> {
                    active(member);
                    conversation(member, id, false);
                    return jdbc.query(
                                    "SELECT * FROM ai_support_turn WHERE conversation_id=? AND"
                                            + " status='SUCCEEDED' ORDER BY id DESC LIMIT 10",
                                    turnMapper(),
                                    id)
                            .reversed();
                });
    }

    Long selected(long member, long id) {
        return tx.execute(
                status -> {
                    active(member);
                    conversation(member, id, false);
                    return jdbc.queryForObject(
                            "SELECT selected_order_id FROM ai_support_conversation WHERE id=?",
                            Long.class,
                            id);
                });
    }

    Turn complete(
            long member,
            long id,
            long turnId,
            Result result,
            String error,
            String modelId,
            long durationMs) {
        return tx.execute(
                status -> {
                    active(member); // Shared member lock serializes with withdrawal, before
                    // locking the conversation.
                    conversation(
                            member, id,
                            true); // Deleted/expired conversations cannot be resurrected by a late
                    // worker.
                    recover(id);
                    Instant now = clock.instant();
                    int updated =
                            jdbc.update(
                                    "UPDATE ai_support_turn SET"
                                        + " status=?,completed_at=?,answer=?,cards_json=?,error_code=?,"
                                        + "model_id=?,prompt_version=?,input_tokens=?,output_tokens=?,"
                                        + "duration_ms=?,tool_summary_json=? WHERE id=? AND"
                                        + " conversation_id=? AND status='PROCESSING' AND"
                                        + " processing_deadline_at>?",
                                    error == null ? "SUCCEEDED" : "FAILED",
                                    ts(now),
                                    result == null ? null : result.answer(),
                                    json.writeValueAsString(
                                            result == null ? List.of() : result.cards()),
                                    error,
                                    modelId,
                                    SupportRuntime.PROMPT_VERSION,
                                    result == null ? 0 : result.inputTokens(),
                                    result == null ? 0 : result.outputTokens(),
                                    durationMs,
                                    json.writeValueAsString(
                                            result == null ? List.of() : result.toolSummary()),
                                    turnId,
                                    id,
                                    ts(now));
                    if (updated == 1) {
                        updateQuestion(id, turnId, result, error);
                        jdbc.update(
                                "UPDATE ai_support_conversation SET selected_order_id=? WHERE id=?",
                                result != null && error == null ? result.selectedOrderId() : null,
                                id);
                    }
                    return turn(turnId);
                });
    }

    void delete(long member, long id) {
        tx.executeWithoutResult(
                status -> {
                    active(member);
                    conversation(member, id, true);
                    jdbc.update(
                            "DELETE FROM ai_support_conversation WHERE id=? AND member_id=?",
                            id,
                            member);
                });
    }

    @ApplicationModuleListener
    public void withdrawn(MemberWithdrawn event) {
        retention.withdrawn(event);
    }

    @Scheduled(fixedDelayString = "PT1H")
    void purgeExpired() {
        retention.purgeExpired();
    }

    private void recover(long id) {
        int recovered =
                jdbc.update(
                        "UPDATE ai_support_turn SET"
                                + " status='FAILED',error_code='PROCESSING_EXPIRED',completed_at=?"
                                + " WHERE conversation_id=? AND status='PROCESSING' AND"
                                + " processing_deadline_at<=?",
                        ts(clock.instant()),
                        id,
                        ts(clock.instant()));
        if (recovered > 0)
            jdbc.update(
                    "UPDATE ai_support_conversation SET"
                            + " selected_order_id=NULL,active_question_json=NULL WHERE id=?",
                    id);
    }

    private void active(long member) {
        if (!orders.lockActiveMember(member))
            throw new SupportException(401, "INVALID_TOKEN", "로그인이 필요합니다.");
    }

    private Conversation conversation(long member, long id, boolean lock) {
        List<Conversation> found =
                jdbc.query(
                        "SELECT * FROM ai_support_conversation WHERE id=? AND member_id=? AND"
                                + " expires_at>?"
                                + (lock ? " FOR UPDATE" : ""),
                        conversationMapper(),
                        id,
                        member,
                        ts(clock.instant()));
        if (found.isEmpty())
            throw new SupportException(404, "CONVERSATION_NOT_FOUND", "대화를 찾을 수 없거나 보관 기간이 지났습니다.");
        return found.getFirst();
    }

    private Turn running(long id) {
        List<Turn> turns =
                jdbc.query(
                        "SELECT * FROM ai_support_turn WHERE conversation_id=? AND"
                                + " status='PROCESSING' ORDER BY id DESC LIMIT 1",
                        turnMapper(),
                        id);
        return turns.isEmpty() ? null : turns.getFirst();
    }

    private Turn turn(long id) {
        return jdbc.queryForObject("SELECT * FROM ai_support_turn WHERE id=?", turnMapper(), id);
    }

    private RowMapper<Conversation> conversationMapper() {
        return (rs, n) ->
                new Conversation(
                        rs.getLong("id"),
                        rs.getString("title"),
                        instant(rs, "created_at"),
                        instant(rs, "last_activity_at"),
                        instant(rs, "expires_at"));
    }

    @SuppressWarnings("unchecked")
    private RowMapper<Turn> turnMapper() {
        return (rs, n) ->
                new Turn(
                        rs.getLong("id"),
                        rs.getString("client_request_id"),
                        json.readValue(rs.getString("input_json"), Input.class),
                        TurnStatus.valueOf(rs.getString("status")),
                        instant(rs, "created_at"),
                        instant(rs, "processing_deadline_at"),
                        rs.getString("answer"),
                        rs.getString("cards_json") == null
                                ? List.of()
                                : json.readValue(rs.getString("cards_json"), List.class),
                        rs.getString("error_code"));
    }

    private long insert(String sql, Object... args) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(
                connection -> {
                    var statement =
                            connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    for (int i = 0; i < args.length; i++)
                        statement.setObject(
                                i + 1, args[i] instanceof Instant instant ? ts(instant) : args[i]);
                    return statement;
                },
                keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    private static LocalDateTime ts(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, LocalDateTime.class).toInstant(ZoneOffset.UTC);
    }
}
