package setty.payment.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import setty.payment.domain.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    boolean existsByOrderId(Long orderId);

    Optional<Payment> findByOrderId(Long orderId);
}
