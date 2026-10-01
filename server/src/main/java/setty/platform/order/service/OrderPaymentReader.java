package setty.platform.order.service;

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

    @Override
    @Transactional(readOnly = true)
    public void verifyBuyer(final Long orderId, final Long buyerId) {
        if (orderRepository.findByIdAndBuyerId(orderId, buyerId).isEmpty()) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public int expectedAmount(final Long orderId) {
        final Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        final Listing listing = listingRepository.findByIdAndDeletedAtIsNull(order.getListingId())
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND));
        return listing.getTotalPrice();
    }
}
