package setty.notification.order.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import setty.notification.order.domain.OrderNotification;

public interface OrderNotificationRepository extends JpaRepository<OrderNotification, Long> {

    List<OrderNotification> findAllByIdIn(List<Long> ids);

    @Modifying
    @Query("delete from OrderNotification notification where notification.memberId = :memberId")
    void deleteAllByMemberId(@Param("memberId") Long memberId);
}
