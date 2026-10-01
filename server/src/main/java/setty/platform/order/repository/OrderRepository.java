package setty.platform.order.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import setty.platform.order.domain.Order;
import setty.platform.order.domain.OrderStatus;

public interface OrderRepository extends JpaRepository<Order, Long> {

    boolean existsByListingIdAndOrderStatusNotIn(Long listingId, List<OrderStatus> closedStatuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :orderId")
    Optional<Order> findByIdForUpdate(@Param("orderId") Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :orderId and o.buyerId = :buyerId")
    Optional<Order> findByIdAndBuyerIdForUpdate(
            @Param("orderId") Long orderId,
            @Param("buyerId") Long buyerId
    );

    @Query("""
            select o.id
            from Order o
            where o.orderStatus = :orderStatus
              and o.pendingExpiresAt <= :referenceTime
            order by o.pendingExpiresAt asc
            """)
    List<Long> findExpiredOrderIds(
            @Param("orderStatus") OrderStatus orderStatus,
            @Param("referenceTime") Instant referenceTime
    );

    List<Order> findAllByBuyerIdOrderByIdDesc(Long buyerId);

    Optional<Order> findByIdAndBuyerId(Long id, Long buyerId);
}
