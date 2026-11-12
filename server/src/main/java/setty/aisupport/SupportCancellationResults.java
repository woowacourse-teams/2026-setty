package setty.aisupport;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import setty.common.OrderCancellationDeclined;
import setty.common.OrderCancelled;
import setty.platform.OrderSupportReader;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class SupportCancellationResults {
    private static final Logger log = LoggerFactory.getLogger(SupportCancellationResults.class);

    record Outcome(
            String requestId,
            long orderId,
            String status,
            Instant acceptedAt,
            Instant decidedAt,
            String reasonCode) {}

    private final JdbcTemplate jdbc;
    private final OrderSupportReader orders;

    @Transactional
    public Outcome get(long member, String requestId) {
        if (!orders.lockActiveMember(member))
            throw new SupportException(401, "INVALID_TOKEN", "로그인이 필요합니다.");
        return jdbc
                .query(
                        "SELECT * FROM ai_support_cancellation WHERE request_id=? AND member_id=?",
                        (rs, n) -> {
                            LocalDateTime decided = rs.getObject("decided_at", LocalDateTime.class);
                            return new Outcome(
                                    rs.getString("request_id"),
                                    rs.getLong("order_id"),
                                    rs.getString("status"),
                                    rs.getObject("accepted_at", LocalDateTime.class)
                                            .toInstant(ZoneOffset.UTC),
                                    decided == null ? null : decided.toInstant(ZoneOffset.UTC),
                                    rs.getString("reason_code"));
                        },
                        requestId,
                        member)
                .stream()
                .findFirst()
                .orElseThrow(
                        () ->
                                new SupportException(
                                        404, "CANCELLATION_NOT_FOUND", "취소 처리 기록을 찾을 수 없습니다."));
    }

    @Transactional
    public Optional<Outcome> latest(long member, long order) {
        if (!orders.lockActiveMember(member))
            throw new SupportException(401, "INVALID_TOKEN", "로그인이 필요합니다.");
        return jdbc
                .query(
                        "SELECT request_id FROM ai_support_cancellation WHERE member_id=? AND"
                            + " order_id=? ORDER BY accepted_at DESC, consent_turn_id DESC LIMIT 1",
                        (rs, n) -> rs.getString(1),
                        member,
                        order)
                .stream()
                .findFirst()
                .map(id -> get(member, id));
    }

    @ApplicationModuleListener
    public void completed(OrderCancelled event) {
        finish(
                event.orderId(),
                event.cancellationRequestId(),
                event.cancelledAt(),
                "COMPLETED",
                "CANCELLED");
    }

    @ApplicationModuleListener
    public void rejected(OrderCancellationDeclined event) {
        finish(
                event.orderId(),
                event.cancellationRequestId(),
                event.decidedAt(),
                "REJECTED",
                event.reasonCode());
    }

    private void finish(long order, String request, Instant at, String status, String reason) {
        // Terminal results cannot be reverted by duplicates, stale requests, or out-of-order
        // deliveries.
        int changed =
                jdbc.update(
                        "UPDATE ai_support_cancellation SET status=?,decided_at=?,reason_code=?"
                            + " WHERE request_id=? AND order_id=? AND status='ACCEPTED'",
                        status,
                        LocalDateTime.ofInstant(at, ZoneOffset.UTC),
                        reason,
                        request,
                        order);
        log.info(
                "support_result order={} cancellation={} incoming={} outcome={}",
                order,
                request,
                status,
                changed == 1 ? "RECORDED" : "IGNORED_STALE_OR_DUPLICATE");
    }
}
