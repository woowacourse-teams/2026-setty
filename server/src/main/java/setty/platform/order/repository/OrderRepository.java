package setty.platform.order.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import setty.platform.order.domain.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {

    boolean existsByListingId(Long listingId);

    List<Order> findAllByBuyerIdOrderByIdDesc(Long buyerId);

    Optional<Order> findByIdAndBuyerId(Long id, Long buyerId);
}
