package setty.delivery.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
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
                requiredInstant(resultSet, "requested_at")
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
                requiredInstant(resultSet, "requested_at")
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
                nullableInstant(resultSet, "accepted_at")
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
                nullableInstant(resultSet, "delivered_at")
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
