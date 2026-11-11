package setty.platform.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.platform.OrderSupportCancellation;
import setty.platform.OrderSupportReader;
import setty.platform.order.repository.OrderRepository;

@Service
@RequiredArgsConstructor
public class OrderSupportCancellationService implements OrderSupportCancellation {
    private final OrderRepository orders;
    private final OrderSupportReader reader;
    private final OrderService service;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Outcome request(long buyerId, long orderId) {
        var order = orders.findByIdAndBuyerIdForUpdate(orderId, buyerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        // Read current state after acquiring the order lock. Delivery may still change later;
        // the existing asynchronous delivery decision remains authoritative.
        var current = reader.getContext(buyerId, orderId);
        if (current.eligibility() != OrderSupportReader.Eligibility.ELIGIBLE) return new Outcome(current, null);
        service.requestCancellation(orderId, buyerId);
        return new Outcome(current, order.getCancellationRequestId());
    }
}
