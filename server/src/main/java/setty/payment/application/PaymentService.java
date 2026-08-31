package setty.payment.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.payment.domain.Payment;
import setty.payment.infrastructure.TossConfirmResult;
import setty.payment.infrastructure.TossPaymentClient;
import setty.payment.presentation.dto.PaymentConfirmRequest;
import setty.platform.listing.domain.Listing;
import setty.platform.listing.repository.ListingRepository;
import setty.platform.member.domain.Member;
import setty.platform.order.repository.OrderRepository;

/**
 * 결제 승인 흐름을 조율한다.
 *
 * <p>순서가 중요하다. 토스 승인(외부 HTTP)은 트랜잭션 밖에서 호출하고,
 * 승인이 확정된 뒤에야 {@link PaymentRegistrar}가 주문 생성 + 결제 저장을 한 트랜잭션으로 처리한다.
 * 그래서 결제 전에 매물이 선점되거나 OrderRequested가 발행되는 일이 없다.
 *
 * <p>platform 패키지는 {@link ListingRepository}·{@link OrderRepository} 조회로 <b>읽기만</b> 한다.
 * 상태 변경(매물 선점·주문 저장·이벤트 발행)은 {@code OrderService.create()}에 위임한다.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final ListingRepository listingRepository;
    private final OrderRepository orderRepository;
    private final TossPaymentClient tossPaymentClient;
    private final PaymentRegistrar paymentRegistrar;

    public Payment confirm(final PaymentConfirmRequest request, final Member buyer) {
        validateBeforeCharge(request, buyer);

        final TossConfirmResult result = tossPaymentClient.confirm(
                request.paymentKey(),
                request.tossOrderId(),
                request.amount()
        );

        return paymentRegistrar.register(request, buyer, result);
    }

    private void validateBeforeCharge(final PaymentConfirmRequest request, final Member buyer) {
        final Listing listing = listingRepository.findByIdAndDeletedAtIsNull(request.listingId())
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND));

        if (listing.isOwnedBy(buyer.getId())) {
            throw new BusinessException(ErrorCode.CANNOT_ORDER_OWN_LISTING);
        }
        if (listing.getTotalPrice() != request.amount()) {
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
        if (orderRepository.existsByListingId(request.listingId())) {
            throw new BusinessException(ErrorCode.ALREADY_ORDERED);
        }
    }
}
