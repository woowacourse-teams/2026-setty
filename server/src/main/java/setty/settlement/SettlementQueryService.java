package setty.settlement;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.settlement.domain.PayeeType;
import setty.settlement.domain.Settlement;
import setty.settlement.persistence.SettlementRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementQueryService {

    private final SettlementRepository settlementRepository;

    public SettlementsView findSeller(final Long sellerId) {
        return find(PayeeType.SELLER, sellerId);
    }

    public SettlementsView findDriver(final Long driverId) {
        return find(PayeeType.DRIVER, driverId);
    }

    private SettlementsView find(final PayeeType payeeType, final Long payeeId) {
        final List<Settlement> settlements = settlementRepository.findAllByPayeeTypeAndPayeeIdOrderByIdDesc(payeeType, payeeId);
        final long confirmedTotal = settlements.stream()
                .filter(Settlement::isConfirmed)
                .mapToLong(Settlement::getAmount)
                .sum();
        return new SettlementsView(confirmedTotal, settlements.stream().map(SettlementQueryService::toSummary).toList());
    }

    private static SettlementSummary toSummary(final Settlement settlement) {
        return new SettlementSummary(
                settlement.getId(),
                settlement.getOrderId(),
                settlement.getListingId(),
                settlement.getAmount(),
                settlement.getStatus().name(),
                settlement.getCreatedAt(),
                settlement.getConfirmedAt(),
                settlement.getCancelledAt()
        );
    }
}
