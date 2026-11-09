package setty.delivery.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;
import setty.delivery.application.DeliveryQueryRepository;
import setty.delivery.application.DeliveryCursor;
import setty.delivery.application.ShipmentGroup;
import setty.delivery.application.readmodel.DeliveryRequest;
import setty.delivery.application.readmodel.Shipment;
import setty.delivery.domain.DeliveryId;
import setty.delivery.domain.DriverId;
import setty.delivery.domain.delivery.DeliveryStatus;

@Repository
@RequiredArgsConstructor
public class JdbcDeliveryQueryRepository implements DeliveryQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<DeliveryRequest.Summary> findAvailableRequests() {
        return findAvailableRequests(null, null);
    }

    @Override
    public List<DeliveryRequest.Summary> findAvailableRequests(final DeliveryCursor cursor, final Integer limit) {
        final StringBuilder sql = new StringBuilder("""
                SELECT id, order_id, item_name, category, pickup_address, delivery_address,
                       pickup_dong_code, delivery_dong_code,
                       estimated_fee, status, requested_at
                FROM delivery
                WHERE status = 'REQUESTED' AND driver_id IS NULL
                """);
        final List<Object> args = new ArrayList<>();
        appendPage(sql, args, "requested_at", cursor, limit);
        return jdbcTemplate.query(sql.toString(), JdbcDeliveryQueryRepository::mapRequestSummary, args.toArray());
    }

    @Override
    public Optional<DeliveryRequest.Detail> findAvailableRequestById(final DeliveryId deliveryId) {
        return jdbcTemplate.query(
                        """
                                SELECT id, order_id, item_name, category,
                                       pickup_address, delivery_address,
                                       pickup_dong_code, delivery_dong_code,
                                       estimated_fee, status, requested_at
                                FROM delivery
                                WHERE id = ? AND status = 'REQUESTED' AND driver_id IS NULL
                                """,
                        JdbcDeliveryQueryRepository::mapRequestDetail,
                        deliveryId.value()
                )
                .stream()
                .findFirst();
    }

    @Override
    public List<Shipment.Summary> findShipmentsByDriverId(final DriverId driverId) {
        return findShipmentsByDriverId(driverId, null, null, null);
    }

    @Override
    public List<Shipment.Summary> findShipmentsByDriverId(final DriverId driverId, final ShipmentGroup group,
                                                        final DeliveryCursor cursor, final Integer limit) {
        final StringBuilder sql = new StringBuilder("""
                SELECT id, order_id, item_name, category, pickup_address, delivery_address,
                       pickup_dong_code, delivery_dong_code,
                       estimated_fee, status, accepted_at
                FROM delivery
                WHERE driver_id = ? AND status
                """);
        sql.append(group == null ? "IN ('ACCEPTED', 'PICKED_UP', 'DELIVERED')" : switch (group) {
            case IN_PROGRESS -> "IN ('ACCEPTED', 'PICKED_UP')";
            case DELIVERED -> "= 'DELIVERED'";
        });
        final List<Object> args = new ArrayList<>();
        args.add(driverId.value());
        appendPage(sql, args, "accepted_at", cursor, limit);
        return jdbcTemplate.query(sql.toString(), JdbcDeliveryQueryRepository::mapShipmentSummary, args.toArray());
    }

    private static void appendPage(final StringBuilder sql, final List<Object> args, final String timeColumn,
                                   final DeliveryCursor cursor, final Integer limit) {
        if (cursor != null) {
            sql.append(" AND (").append(timeColumn).append(" < ? OR (")
                    .append(timeColumn).append(" = ? AND id < ?))");
            args.add(Timestamp.from(cursor.time()));
            args.add(Timestamp.from(cursor.time()));
            args.add(cursor.id());
        }
        sql.append(" ORDER BY ").append(timeColumn).append(" DESC, id DESC");
        if (limit != null) {
            sql.append(" LIMIT ?");
            args.add(limit);
        }
    }

    @Override
    public Optional<Shipment.Detail> findShipmentByIdAndDriverId(
            final DeliveryId deliveryId,
            final DriverId driverId
    ) {
        return jdbcTemplate.query(
                        """
                                SELECT id, order_id, item_name, category,
                                       pickup_address, pickup_phone_number,
                                       delivery_address, delivery_phone_number,
                                       pickup_dong_code, delivery_dong_code,
                                       estimated_fee, status, requested_at,
                                       accepted_at, picked_up_at, delivered_at
                                FROM delivery
                                WHERE id = ? AND driver_id = ?
                                  AND status IN ('ACCEPTED', 'PICKED_UP', 'DELIVERED')
                                """,
                        JdbcDeliveryQueryRepository::mapShipmentDetail,
                        deliveryId.value(),
                        driverId.value()
                )
                .stream()
                .findFirst();
    }

    @Override
    public List<DeliveryRequest.CellCall> findCellCalls() {
        return jdbcTemplate.query("""
                SELECT id, order_id, item_name, category, pickup_address, delivery_address,
                       pickup_dong_code, delivery_dong_code,
                       estimated_fee, status, requested_at, pickup_cell, delivery_cell
                FROM delivery
                WHERE status = 'REQUESTED' AND driver_id IS NULL
                  AND pickup_cell IS NOT NULL AND delivery_cell IS NOT NULL
                ORDER BY pickup_cell, delivery_cell, estimated_fee DESC, id
                """, (resultSet, rowNumber) -> new DeliveryRequest.CellCall(
                resultSet.getInt("pickup_cell"),
                resultSet.getInt("delivery_cell"),
                mapRequestSummary(resultSet, rowNumber)
        ));
    }

    @Override
    public List<DeliveryRequest.Summary> findCellCalls(final int pickupCell, final int deliveryCell, final int limit) {
        return jdbcTemplate.query("""
                SELECT id, order_id, item_name, category, pickup_address, delivery_address,
                       pickup_dong_code, delivery_dong_code,
                       estimated_fee, status, requested_at
                FROM delivery
                WHERE pickup_cell = ? AND delivery_cell = ? AND status = 'REQUESTED' AND driver_id IS NULL
                ORDER BY estimated_fee DESC, id
                LIMIT ?
                """, JdbcDeliveryQueryRepository::mapRequestSummary, pickupCell, deliveryCell, limit);
    }

    @Override
    public Map<Long, Set<Integer>> findInProgressDeliveryCells(final Collection<Long> driverIds) {
        final Map<Long, Set<Integer>> cells = new HashMap<>();
        if (driverIds.isEmpty()) {
            return cells;
        }
        final String placeholders = String.join(",", driverIds.stream().map(id -> "?").toList());
        jdbcTemplate.query("""
                SELECT driver_id, delivery_cell
                FROM delivery
                WHERE status IN ('ACCEPTED', 'PICKED_UP') AND delivery_cell IS NOT NULL AND driver_id IN (%s)
                """.formatted(placeholders), (RowCallbackHandler) resultSet ->
                cells.computeIfAbsent(resultSet.getLong("driver_id"), id -> new HashSet<>())
                        .add(resultSet.getInt("delivery_cell")), driverIds.toArray());
        return cells;
    }

    private static DeliveryRequest.Summary mapRequestSummary(final ResultSet resultSet, final int rowNumber)
            throws SQLException {
        return new DeliveryRequest.Summary(
                resultSet.getLong("id"),
                resultSet.getLong("order_id"),
                resultSet.getString("item_name"),
                resultSet.getString("category"),
                resultSet.getString("pickup_address"),
                resultSet.getString("delivery_address"),
                resultSet.getLong("estimated_fee"),
                status(resultSet),
                requiredInstant(resultSet, "requested_at"),
                resultSet.getString("pickup_dong_code"),
                resultSet.getString("delivery_dong_code")
        );
    }

    private static DeliveryRequest.Detail mapRequestDetail(final ResultSet resultSet, final int rowNumber)
            throws SQLException {
        return new DeliveryRequest.Detail(
                resultSet.getLong("id"),
                resultSet.getLong("order_id"),
                resultSet.getString("item_name"),
                resultSet.getString("category"),
                resultSet.getString("pickup_address"),
                resultSet.getString("delivery_address"),
                resultSet.getLong("estimated_fee"),
                status(resultSet),
                requiredInstant(resultSet, "requested_at"),
                resultSet.getString("pickup_dong_code"),
                resultSet.getString("delivery_dong_code")
        );
    }

    private static Shipment.Summary mapShipmentSummary(final ResultSet resultSet, final int rowNumber)
            throws SQLException {
        return new Shipment.Summary(
                resultSet.getLong("id"),
                resultSet.getLong("order_id"),
                resultSet.getString("item_name"),
                resultSet.getString("category"),
                resultSet.getString("pickup_address"),
                resultSet.getString("delivery_address"),
                resultSet.getLong("estimated_fee"),
                status(resultSet),
                nullableInstant(resultSet, "accepted_at"),
                resultSet.getString("pickup_dong_code"),
                resultSet.getString("delivery_dong_code")
        );
    }

    private static Shipment.Detail mapShipmentDetail(final ResultSet resultSet, final int rowNumber)
            throws SQLException {
        return new Shipment.Detail(
                resultSet.getLong("id"),
                resultSet.getLong("order_id"),
                resultSet.getString("item_name"),
                resultSet.getString("category"),
                resultSet.getString("pickup_address"),
                resultSet.getString("pickup_phone_number"),
                resultSet.getString("delivery_address"),
                resultSet.getString("delivery_phone_number"),
                resultSet.getLong("estimated_fee"),
                status(resultSet),
                requiredInstant(resultSet, "requested_at"),
                nullableInstant(resultSet, "accepted_at"),
                nullableInstant(resultSet, "picked_up_at"),
                nullableInstant(resultSet, "delivered_at"),
                resultSet.getString("pickup_dong_code"),
                resultSet.getString("delivery_dong_code")
        );
    }

    private static DeliveryStatus status(final ResultSet resultSet) throws SQLException {
        return DeliveryStatus.valueOf(resultSet.getString("status"));
    }

    private static Instant requiredInstant(final ResultSet resultSet, final String column) throws SQLException {
        return resultSet.getTimestamp(column).toInstant();
    }

    private static Instant nullableInstant(final ResultSet resultSet, final String column) throws SQLException {
        final Timestamp timestamp = resultSet.getTimestamp(column);
        if (timestamp == null) {
            return null;
        }
        return timestamp.toInstant();
    }
}
