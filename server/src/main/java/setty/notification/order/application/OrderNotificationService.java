package setty.notification.order.application;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.common.OrderStatusNotified;
import setty.common.SettlementConfirmed;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.notification.order.domain.OrderNotification;
import setty.notification.order.domain.OrderNotificationKind;
import setty.notification.order.domain.OrderNotificationRecipient;
import setty.notification.order.repository.OrderNotificationRepository;

@Service
public class OrderNotificationService {

    private final OrderNotificationRepository orderNotificationRepository;
    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public OrderNotificationService(
            final OrderNotificationRepository orderNotificationRepository,
            final JdbcTemplate jdbcTemplate
    ) {
        this.orderNotificationRepository = orderNotificationRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    // 이벤트가 다시 와도 유니크 키로 한 번만 만든다. 아직 못 보낸 이 이벤트의 알림을 돌려준다.
    // 같은 이벤트를 동시에 처리하면 여러 행을 한 트랜잭션에 넣을 때 서로 잠금을 기다려 교착이 나므로, 문장마다 바로 커밋한다.
    public List<PendingOrderNotification> createFor(final OrderStatusNotified event) {
        final Timestamp occurredAt = toTimestamp(event.occurredAt());
        final List<OrderNotificationKind> kinds = OrderNotificationKind.of(event.change());
        for (final OrderNotificationKind kind : kinds) {
            for (final OrderNotificationRecipient recipient : kind.recipients()) {
                insert(event.orderId(), event.listingId(), memberOf(event, recipient), recipient, kind, null, occurredAt);
            }
        }
        return findPending(event.orderId(), occurredAt, kinds);
    }

    // 정산 확정 시각을 occurred_at으로 써서 같은 확정이 다시 와도 한 행이다.
    public List<PendingOrderNotification> createFor(final SettlementConfirmed event) {
        final Timestamp occurredAt = toTimestamp(event.confirmedAt());
        final OrderNotificationKind kind = OrderNotificationKind.SETTLEMENT_CONFIRMED;
        insert(event.orderId(), event.listingId(), event.sellerId(), OrderNotificationRecipient.SELLER, kind,
                event.amount(), occurredAt);
        return findPending(event.orderId(), occurredAt, List.of(kind));
    }

    private static Timestamp toTimestamp(final Instant instant) {
        return Timestamp.from(instant.truncatedTo(ChronoUnit.MICROS));
    }

    private void insert(
            final Long orderId,
            final Long listingId,
            final Long memberId,
            final OrderNotificationRecipient recipient,
            final OrderNotificationKind kind,
            final Integer amount,
            final Timestamp occurredAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO order_notifications
                    (order_id, listing_id, member_id, recipient, kind, amount, status, attempts, occurred_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, 'PENDING', 0, ?, NOW(6))
                ON DUPLICATE KEY UPDATE order_id = order_notifications.order_id
                """,
                orderId, listingId, memberId, recipient.name(), kind.name(), amount, occurredAt
        );
    }

    private List<PendingOrderNotification> findPending(
            final Long orderId,
            final Timestamp occurredAt,
            final List<OrderNotificationKind> kinds
    ) {
        if (kinds.isEmpty()) {
            return List.of();
        }
        return namedJdbcTemplate.query(
                """
                SELECT id, member_id, recipient, kind FROM order_notifications
                WHERE order_id = :orderId AND occurred_at = :occurredAt AND kind IN (:kinds) AND status = 'PENDING'
                ORDER BY id
                """,
                Map.of("orderId", orderId, "occurredAt", occurredAt, "kinds", kinds.stream().map(Enum::name).toList()),
                (resultSet, rowNum) -> new PendingOrderNotification(
                        resultSet.getLong("id"),
                        resultSet.getLong("member_id"),
                        OrderNotificationRecipient.valueOf(resultSet.getString("recipient")),
                        OrderNotificationKind.valueOf(resultSet.getString("kind")))
        );
    }

    private static Long memberOf(final OrderStatusNotified event, final OrderNotificationRecipient recipient) {
        return recipient == OrderNotificationRecipient.BUYER ? event.buyerId() : event.sellerId();
    }

    public void markSent(final List<Long> notificationIds) {
        if (notificationIds.isEmpty()) {
            return;
        }
        namedJdbcTemplate.update(
                """
                UPDATE order_notifications
                SET status = 'SENT', sent_at = NOW(6), attempts = attempts + 1
                WHERE id IN (:ids) AND status = 'PENDING'
                """,
                Map.of("ids", notificationIds)
        );
    }

    // 남은 재시도 대상 수를 돌려준다. 세 번째 실패에서 FAILED가 된다.
    public long markFailedAttempt(final List<Long> notificationIds) {
        if (notificationIds.isEmpty()) {
            return 0;
        }
        namedJdbcTemplate.update(
                """
                UPDATE order_notifications
                SET status = CASE WHEN attempts + 1 >= :max THEN 'FAILED' ELSE 'PENDING' END,
                    attempts = attempts + 1
                WHERE id IN (:ids) AND status = 'PENDING'
                """,
                Map.of("ids", notificationIds, "max", OrderNotification.MAXIMUM_ATTEMPTS)
        );
        return namedJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM order_notifications WHERE id IN (:ids) AND status = 'PENDING'",
                Map.of("ids", notificationIds),
                Long.class
        );
    }

    public void ack(final Long memberId, final Long notificationId) {
        requireMatched(jdbcTemplate.update(
                "UPDATE order_notifications SET acked_at = COALESCE(acked_at, NOW(6)) WHERE id = ? AND member_id = ?",
                notificationId, memberId));
    }

    public void read(final Long memberId, final Long notificationId) {
        requireMatched(jdbcTemplate.update(
                "UPDATE order_notifications SET read_at = COALESCE(read_at, NOW(6)) WHERE id = ? AND member_id = ?",
                notificationId, memberId));
    }

    private static void requireMatched(final int matched) {
        if (matched == 0) {
            throw new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND);
        }
    }

    public long countUnread(final Long memberId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM order_notifications WHERE member_id = ? AND status = 'SENT' AND read_at IS NULL",
                Long.class,
                memberId
        );
    }

    public void readAll(final Long memberId) {
        jdbcTemplate.update(
                "UPDATE order_notifications SET read_at = NOW(6) WHERE member_id = ? AND status = 'SENT' AND read_at IS NULL",
                memberId
        );
    }

    @Transactional(readOnly = true)
    public List<OrderNotification> findAll(final List<Long> notificationIds) {
        if (notificationIds.isEmpty()) {
            return List.of();
        }
        return orderNotificationRepository.findAllByIdIn(notificationIds);
    }

    @Transactional
    public void deleteAllOf(final Long memberId) {
        orderNotificationRepository.deleteAllByMemberId(memberId);
    }
}
