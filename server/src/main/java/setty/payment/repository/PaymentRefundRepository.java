package setty.payment.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import setty.payment.domain.PaymentRefund;
import setty.payment.domain.PaymentRefundStatus;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, Long> {

    boolean existsByPaymentId(Long paymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM PaymentRefund r WHERE r.id = :id")
    Optional<PaymentRefund> findByIdForUpdate(@Param("id") Long id);

    List<PaymentRefund> findByStatusOrderByIdAsc(PaymentRefundStatus status, Limit limit);
}
