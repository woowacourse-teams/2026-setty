package setty.settlement.application;

import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.settlement.domain.Settlement;
import setty.settlement.domain.SettlementDecision;
import setty.settlement.persistence.SettlementOrderRepository;
import setty.settlement.persistence.SettlementRepository;

/**
 * 정산 행 기록과 주문 결론 반영은 어느 쪽이 먼저 와도 같은 결과가 되도록 처리한다.
 * 정산 행이 먼저면 결론이 올 때 함께 바꾸고, 결론이 먼저면 정산 행을 기록할 때 바로 적용한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);

    private final SettlementRepository settlementRepository;
    private final SettlementOrderRepository settlementOrderRepository;

    public void recordSeller(
            final Long orderId,
            final Long sellerId,
            final Long listingId,
            final int itemPrice,
            final Instant recordedAt
    ) {
        record(Settlement.seller(orderId, sellerId, listingId, itemPrice, recordedAt));
    }

    public void recordDriver(final Long orderId, final Long driverId, final int deliveryFee, final Instant recordedAt) {
        record(Settlement.driver(orderId, driverId, deliveryFee, recordedAt));
    }

    public void complete(final Long orderId, final Instant completedAt) {
        decide(orderId, SettlementDecision.completed(completedAt));
    }

    public void cancel(final Long orderId, final Instant cancelledAt) {
        decide(orderId, SettlementDecision.cancelled(cancelledAt));
    }

    // 잠금을 트랜잭션의 첫 조회보다 먼저 잡아야 앞서 잠갔던 트랜잭션이 커밋한 행을 본다.
    private void record(final Settlement settlement) {
        final Optional<SettlementDecision> decision = settlementOrderRepository.lock(settlement.getOrderId());
        if (settlementRepository.existsByOrderIdAndPayeeType(settlement.getOrderId(), settlement.getPayeeType())) {
            return;
        }
        decision.ifPresent(settlement::apply);
        settlementRepository.save(settlement);
    }

    private void decide(final Long orderId, final SettlementDecision decision) {
        if (orderId == null || orderId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        final Optional<SettlementDecision> existing = settlementOrderRepository.lock(orderId);
        if (existing.isPresent()) {
            if (existing.get().status() != decision.status()) {
                log.warn("이미 {}로 결론 난 주문에 {} 결론이 도착해 무시합니다. orderId={}",
                        existing.get().status(), decision.status(), orderId);
            }
            return;
        }
        settlementOrderRepository.decide(orderId, decision);
        settlementRepository.findAllByOrderId(orderId).forEach(settlement -> settlement.apply(decision));
    }
}
