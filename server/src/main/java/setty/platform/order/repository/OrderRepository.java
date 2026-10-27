package setty.platform.order.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import setty.platform.order.domain.Order;
import setty.platform.order.domain.OrderStatus;

public interface OrderRepository extends JpaRepository<Order, Long> {

    boolean existsByListingIdAndOrderStatusNotIn(Long listingId, List<OrderStatus> closedStatuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :orderId")
    Optional<Order> findByIdForUpdate(@Param("orderId") Long orderId);

    // 큰 IN 조건에서 인덱스 전체 스캔으로 무관한 주문 INSERT까지 막지 않도록 PK 범위 조회를 유도한다.
    @Query(value = "select * from orders force index (PRIMARY) where id in (:orderIds) order by id for update",
            nativeQuery = true)
    List<Order> findAllByIdForUpdate(@Param("orderIds") List<Long> orderIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update orders force index (PRIMARY)
            set order_status = 'EXPIRED'
            where id in (:orderIds)
              and order_status = 'PENDING'
              and pending_expires_at <= :referenceTime
            """, nativeQuery = true)
    int expirePendingOrders(
            @Param("orderIds") List<Long> orderIds,
            @Param("referenceTime") Instant referenceTime
    );

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

    @Query("""
            select o.id
            from Order o
            where o.orderStatus = setty.platform.order.domain.OrderStatus.CONFIRMED
              and o.deliveryStatus = setty.common.DeliveryStatus.DELIVERED
              and o.deliveryStatusChangedAt <= :deliveredBefore
            order by o.deliveryStatusChangedAt asc
            """)
    List<Long> findCompletionDueOrderIds(@Param("deliveredBefore") Instant deliveredBefore);

    @Query("""
            select o
            from Order o
            where o.listingId = :listingId
              and o.buyerId = :buyerId
              and o.orderStatus = setty.platform.order.domain.OrderStatus.PENDING
              and o.pendingExpiresAt > :referenceTime
            """)
    Optional<Order> findPayableByListingIdAndBuyerId(
            @Param("listingId") Long listingId,
            @Param("buyerId") Long buyerId,
            @Param("referenceTime") Instant referenceTime
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o
            from Order o
            where o.listingId = :listingId
              and o.buyerId = :buyerId
              and o.orderStatus = setty.platform.order.domain.OrderStatus.PENDING
              and o.pendingExpiresAt > :referenceTime
            """)
    Optional<Order> findPayableByListingIdAndBuyerIdForUpdate(
            @Param("listingId") Long listingId,
            @Param("buyerId") Long buyerId,
            @Param("referenceTime") Instant referenceTime
    );

    List<Order> findAllByBuyerIdOrderByIdDesc(Long buyerId);

    List<Order> findAllByListingIdInAndOrderStatusIn(List<Long> listingIds, List<OrderStatus> statuses);

    List<Order> findAllByListingIdAndBuyerIdAndOrderStatusInOrderByIdDesc(
            Long listingId,
            Long buyerId,
            List<OrderStatus> statuses
    );

    Optional<Order> findByIdAndBuyerId(Long id, Long buyerId);
}
