package setty.settlement.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import setty.settlement.domain.PayeeType;
import setty.settlement.domain.Settlement;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    boolean existsByOrderIdAndPayeeType(Long orderId, PayeeType payeeType);

    List<Settlement> findAllByOrderId(Long orderId);
}
