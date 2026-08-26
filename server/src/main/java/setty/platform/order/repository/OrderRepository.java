package setty.platform.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import setty.platform.order.domain.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {

    boolean existsByListingId(Long listingId);
}
