package setty.platform.order.service;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.platform.PaymentOrderReader;
import setty.platform.listing.domain.Listing;
import setty.platform.listing.repository.ListingRepository;
import setty.platform.order.domain.Order;
import setty.platform.order.repository.OrderRepository;

@Service
@RequiredArgsConstructor
public class OrderPaymentReader implements PaymentOrderReader {

    private final OrderRepository orderRepository;
    private final ListingRepository listingRepository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public void verifyBuyer(final Long orderId, final Long buyerId) {
        if (orderRepository.findByIdAndBuyerId(orderId, buyerId).isEmpty()) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public int payableAmount(final Long orderId) {
        final Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        // 만료 스캔 전이라도 만료 시각이 지났으면 토스 승인으로 넘기지 않는다.
        if (!order.isPayable(clock.instant())) {
            throw new BusinessException(ErrorCode.ORDER_NOT_PAYABLE);
        }
        final Listing listing = listingRepository.findByIdAndDeletedAtIsNull(order.getListingId())
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND));
        return listing.getTotalPrice();
    }
}
