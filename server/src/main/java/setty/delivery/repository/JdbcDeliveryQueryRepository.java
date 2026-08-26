package setty.delivery.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import setty.common.DeliveryStatus;
import setty.delivery.domain.DeliveryId;
import setty.delivery.domain.DriverId;
import setty.delivery.query.DeliveryRequest;
import setty.delivery.query.Shipment;

@Repository
public class JdbcDeliveryQueryRepository implements DeliveryQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcDeliveryQueryRepository(final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<DeliveryRequest.Summary> findAvailableRequests() {
        return jdbcTemplate.query(
                """
                SELECT id, item_name, category, pickup_address, delivery_address,
                       estimated_fee, status, requested_at
                FROM delivery
                WHERE status = 'REQUESTED' AND driver_id IS NULL
                ORDER BY requested_at DESC, id DESC
                """,
                JdbcDeliveryQueryRepository::mapRequestSummary
        );
    }

    @Override
    public Optional<DeliveryRequest.Detail> findAvailableRequestById(final DeliveryId deliveryId) {
        return jdbcTemplate.query(
                        """
                        SELECT id, order_id, item_name, category,
                               pickup_address, pickup_phone_number,
                               delivery_address, delivery_phone_number,
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
        return jdbcTemplate.query(
                """
                SELECT id, item_name, category, pickup_address, delivery_address,
                       estimated_fee, status, accepted_at
                FROM delivery
                WHERE driver_id = ? AND status IN ('ACCEPTED', 'PICKED_UP', 'DELIVERED')
                ORDER BY accepted_at DESC, id DESC
                """,
                JdbcDeliveryQueryRepository::mapShipmentSummary,
                driverId.value()
        );
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
                resultSet.getString("pickup_phone_number"),
                resultSet.getString("delivery_address"),
                resultSet.getString("delivery_phone_number"),
                resultSet.getLong("estimated_fee"),
                status(resultSet),
                requiredInstant(resultSet, "requested_at")
        );
    }

    private static Shipment.Summary mapShipmentSummary(final ResultSet resultSet, final int rowNumber)
            throws SQLException {
        return new Shipment.Summary(
                resultSet.getLong("id"),
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
