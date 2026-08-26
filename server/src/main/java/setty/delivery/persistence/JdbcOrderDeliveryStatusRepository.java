package setty.delivery.persistence;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import setty.common.DeliveryStatus;
import setty.delivery.application.OrderDeliveryStatusRepository;
import setty.delivery.domain.OrderDeliveryState;
import setty.delivery.domain.OrderId;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

@Repository
@RequiredArgsConstructor
public class JdbcOrderDeliveryStatusRepository implements OrderDeliveryStatusRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Optional<OrderDeliveryState> findById(final OrderId orderId) {
        return jdbcTemplate.query(
                        "SELECT id, delivery_status FROM orders WHERE id = ? FOR UPDATE",
                        (resultSet, rowNumber) -> new OrderDeliveryState(
                                new OrderId(resultSet.getLong("id")),
                                DeliveryStatus.valueOf(resultSet.getString("delivery_status"))
                        ),
                        orderId.value()
                )
                .stream()
                .findFirst();
    }

    @Override
    public void save(final OrderDeliveryState orderDeliveryState) {
        final int updatedRows = jdbcTemplate.update(
                "UPDATE orders SET delivery_status = ? WHERE id = ?",
                orderDeliveryState.getStatus().name(),
                orderDeliveryState.getOrderId().value()
        );
        if (updatedRows != 1) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
    }
}
