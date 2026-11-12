package setty.platform.order.service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.platform.DeliverySupportReader;
import setty.platform.OrderSupportReader;
import setty.platform.member.repository.MemberRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderSupportQuery implements OrderSupportReader {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int MAX_PAGE_SIZE = 20;
    private static final String POLICY_VERSION = "cancellation-read-v1";
    private static final String SELECT = "SELECT o.id, l.title, UNIX_TIMESTAMP(o.created_at) created_epoch, "
            + "o.order_status, o.delivery_status, (l.price + COALESCE(o.delivery_fee,l.delivery_fee)) total_price, "
            + "(SELECT i.object_key FROM listing_images i WHERE i.listing_id=l.id ORDER BY i.display_order LIMIT 1) thumbnail_key "
            + "FROM orders o LEFT JOIN listings l ON l.id=o.listing_id ";
    private final JdbcTemplate jdbc;
    private final setty.platform.listing.storage.ListingImageStorage images;
    private final DeliverySupportReader deliveryReader;
    private final MemberRepository members;
    private final Clock clock;

    @Override
    public SearchResult findMyOrders(final Long buyerId, final Search query) {
        requirePositive(buyerId);
        if (query == null || query.title() != null && query.title().length() > 200
                || query.fromDate() != null && query.toDate() != null
                && query.fromDate().isAfter(query.toDate())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (query.cancelledOnly() && (query.latestOnly() || query.cancellationTargets())) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        if (query.latestOnly() && query.beforeId() != null) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        final int pageSize = query.latestOnly() ? 1 : query.pageSize() == null ? 5 : query.pageSize();
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        final StringBuilder where = new StringBuilder(" WHERE o.buyer_id=?");
        final List<Object> args = new ArrayList<>();
        args.add(buyerId);
        if (query.cancelledOnly()) where.append(" AND o.order_status='CANCELLED'");
        if (query.cancellationTargets() && query.orderId() == null) where.append(" AND o.order_status<>'CANCELLED'");
        if (query.excludedOrderId() != null) {
            requirePositive(query.excludedOrderId());
            where.append(" AND o.id<>?"); args.add(query.excludedOrderId());
        }
        if (query.orderId() != null) {
            requirePositive(query.orderId());
            where.append(" AND o.id=?");
            args.add(query.orderId());
        }
        if (query.title() != null && !query.title().isBlank()) {
            where.append(" AND l.title LIKE ? ESCAPE '!'");
            args.add("%" + query.title().trim().replace("!", "!!").replace("%", "!%")
                    .replace("_", "!_") + "%");
        }
        // 날짜가 없는 과거 주문은 다른 검색 단서 범위에서만 알린다. 다른 회원 정보는 집계하지 않는다.
        final boolean hasDate = query.fromDate() != null || query.toDate() != null;
        final long excludedUndatedCount = hasDate ? jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders o LEFT JOIN listings l ON l.id=o.listing_id"
                        + where + " AND o.created_at IS NULL", Long.class, args.toArray()) : 0;
        if (query.fromDate() != null) {
            where.append(" AND o.created_at>=FROM_UNIXTIME(?)");
            args.add(query.fromDate().atStartOfDay(KST).toEpochSecond());
        }
        if (query.toDate() != null) {
            where.append(" AND o.created_at<FROM_UNIXTIME(?)");
            args.add(query.toDate().plusDays(1).atStartOfDay(KST).toEpochSecond());
        }
        if (query.beforeId() != null) {
            requirePositive(query.beforeId());
            // Cursor is scoped to the authenticated buyer for every search kind.
            where.append(" AND o.id<? AND EXISTS(SELECT 1 FROM orders cursor_order WHERE cursor_order.id=? AND cursor_order.buyer_id=?)");
            args.add(query.beforeId()); args.add(query.beforeId()); args.add(buyerId);
        }
        final String ordering = " ORDER BY o.id DESC LIMIT " + (query.latestOnly() ? 1 : pageSize + 1);
        final List<Candidate> found = jdbc.query(SELECT + where + ordering,
                this::candidate, args.toArray());
        final boolean more = found.size() > pageSize;
        final List<Candidate> page = List.copyOf(found.subList(0, Math.min(pageSize, found.size())));
        return new SearchResult(page, more ? page.getLast().orderId() : null, excludedUndatedCount > 0, excludedUndatedCount);
    }

    @Override
    public Context getContext(final Long buyerId, final Long orderId) {
        requirePositive(buyerId);
        requirePositive(orderId);
        final Candidate order = jdbc.query(SELECT + "WHERE o.buyer_id=? AND o.id=?",
                        this::candidate, buyerId, orderId).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        final Instant observedAt = clock.instant();
        if ("PAID".equals(order.orderStatus())) {
            return context(order, null, Eligibility.INELIGIBLE, "ORDER_CONFIRMATION_PENDING", observedAt);
        }
        final Optional<DeliverySupportReader.State> delivery;
        try {
            delivery = deliveryReader.findState(orderId);
        } catch (DataAccessException exception) {
            return context(order, null, Eligibility.UNKNOWN, "DELIVERY_LOOKUP_FAILED", observedAt);
        }
        final String status = delivery.map(Enum::name).orElse("ABSENT");
        return switch (order.orderStatus()) {
            case "CANCEL_PENDING" -> context(order, status, Eligibility.INELIGIBLE, "CANCELLATION_PENDING", observedAt);
            case "CANCELLED" -> context(order, status, Eligibility.INELIGIBLE, "ALREADY_CANCELLED", observedAt);
            case "COMPLETED" -> context(order, status, Eligibility.INELIGIBLE, "PURCHASE_COMPLETED", observedAt);
            case "PENDING" -> context(order, status, Eligibility.INELIGIBLE, "PAYMENT_PENDING", observedAt);
            case "EXPIRED" -> context(order, status, Eligibility.INELIGIBLE, "ORDER_EXPIRED", observedAt);
            case "CONFIRMED" -> switch (status) {
                case "ABSENT", "REQUESTED" -> context(order, status, Eligibility.ELIGIBLE, "BEFORE_DRIVER_ACCEPTANCE", observedAt);
                case "ACCEPTED" -> context(order, status, Eligibility.INELIGIBLE, "DRIVER_ACCEPTED", observedAt);
                case "PICKED_UP" -> context(order, status, Eligibility.INELIGIBLE, "ALREADY_PICKED_UP", observedAt);
                case "DELIVERED" -> context(order, status, Eligibility.INELIGIBLE, "ALREADY_DELIVERED", observedAt);
                default -> context(order, status, Eligibility.UNKNOWN, "STATE_NOT_SYNCHRONIZED", observedAt);
            };
            default -> context(order, status, Eligibility.UNKNOWN, "UNKNOWN_ORDER_STATUS", observedAt);
        };
    }

    @Override
    public boolean isActiveMember(final Long buyerId) {
        return buyerId != null && Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM members WHERE id=? AND withdrawn_at IS NULL)", Boolean.class, buyerId));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean lockActiveMember(final Long buyerId) {
        return buyerId != null && members.lockActiveIdForShare(buyerId).isPresent();
    }

    private static Context context(final Candidate order, final String status, final Eligibility eligibility,
                                   final String reason, final Instant at) {
        return new Context(order, status, eligibility, reason, at, POLICY_VERSION);
    }

    private Candidate candidate(final ResultSet rs, final int row) throws SQLException {
        final BigDecimal epoch = rs.getBigDecimal("created_epoch");
        final Instant createdAt = epoch == null ? null : Instant.ofEpochSecond(epoch.longValue(),
                epoch.remainder(BigDecimal.ONE).movePointRight(9).longValue());
        return new Candidate(rs.getLong("id"), rs.getString("title"), createdAt, rs.getString("order_status"),
                rs.getString("thumbnail_key") == null ? null : images.publicUrl(rs.getString("thumbnail_key")),
                rs.getObject("total_price", Integer.class), rs.getString("delivery_status"));
    }

    private static void requirePositive(final Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }
}
