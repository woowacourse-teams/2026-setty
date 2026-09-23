package setty.platform.order.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import setty.common.DeliveryStatus;
import setty.platform.order.domain.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {

    boolean existsByListingId(Long listingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :orderId")
    Optional<Order> findByIdForUpdate(@Param("orderId") Long orderId);

    @Query("""
            select o.id
            from Order o
            where o.deliveryStatus = :deliveryStatus
              and o.pendingExpiresAt <= :referenceTime
            order by o.pendingExpiresAt asc
            """)
    List<Long> findExpiredOrderIds(
            @Param("deliveryStatus") DeliveryStatus deliveryStatus,
            @Param("referenceTime") Instant referenceTime
    );

    @Query(value = "SELECT COUNT(*) FROM payments WHERE order_id = :orderId", nativeQuery = true)
    long countPaymentReferences(@Param("orderId") Long orderId);

    List<Order> findAllByBuyerIdOrderByIdDesc(Long buyerId);

    Optional<Order> findByIdAndBuyerId(Long id, Long buyerId);
}
