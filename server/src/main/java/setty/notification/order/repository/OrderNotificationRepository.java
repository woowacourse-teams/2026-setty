package setty.notification.order.repository;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import setty.notification.order.domain.OrderNotification;
import setty.notification.owner.OwnerType;

public interface OrderNotificationRepository extends JpaRepository<OrderNotification, Long> {

    List<OrderNotification> findAllByIdIn(List<Long> ids);

    List<OrderNotification> findAllByOwnerTypeAndOwnerIdOrderByCreatedAtDescIdDesc(
            OwnerType ownerType,
            Long ownerId,
            Pageable pageable
    );

    @Modifying
    @Query("delete from OrderNotification notification "
            + "where notification.ownerType = :ownerType and notification.ownerId = :ownerId")
    void deleteAllByOwner(@Param("ownerType") OwnerType ownerType, @Param("ownerId") Long ownerId);
}
