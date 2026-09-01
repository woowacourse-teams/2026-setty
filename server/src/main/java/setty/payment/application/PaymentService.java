package setty.payment.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.payment.domain.Payment;
import setty.payment.infrastructure.TossConfirmResult;
import setty.payment.infrastructure.TossPaymentClient;
import setty.payment.repository.PaymentRepository;
import setty.platform.listing.domain.Listing;
import setty.platform.listing.repository.ListingRepository;
import setty.platform.order.domain.Order;
import setty.platform.order.repository.OrderRepository;

/**
 * 결제 승인·실패 흐름을 조율한다.
 *
 * <p>주문은 결제 이전에 PENDING으로 먼저 존재한다(order 팀). payment는 주문을 만들지 않고,
 * {@code orderId}를 앵커로 주문·매물을 <b>읽기만</b> 해서 검증한 뒤 결과를 이벤트로 알린다.
 *
 * <p>순서가 중요하다. 토스 승인(외부 HTTP)은 트랜잭션 밖에서 호출하고,
 * 승인이 확정된 뒤에야 {@link PaymentRecorder}가 결제 저장 + 이벤트 발행을 한 트랜잭션으로 처리한다.
 *
 * <p>매물 상태·주문 만료/선점 판정은 읽지 않는다 — 그 판정과 보상(만료 주문 취소 등)은
 * {@code PaymentCompleted}/{@code PaymentFailed}를 수신하는 플랫폼(주문) 팀이 담당한다.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final OrderRepository orderRepository;
    private final ListingRepository listingRepository;
    private final PaymentRepository paymentRepository;
    private final TossPaymentClient tossPaymentClient;
    private final PaymentRecorder paymentRecorder;

    /**
     * 결제 성공 복귀 처리. 매물 가격으로 금액을 재검증한 뒤 토스 승인을 호출하고 결제를 저장한다.
     * 같은 주문이 이미 승인 완료면 재승인 없이 기존 결제를 그대로 돌려준다(멱등).
     */
    public Payment confirm(final Long orderId, final String paymentKey, final int amount) {
        final int expectedAmount = resolveExpectedAmount(orderId);
        if (expectedAmount != amount) {
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        final Payment alreadyPaid = paymentRepository.findByOrderId(orderId).orElse(null);
        if (alreadyPaid != null && alreadyPaid.isDone()) {
            return alreadyPaid;
        }

        final TossConfirmResult result = tossPaymentClient.confirm(paymentKey, String.valueOf(orderId), amount);

        return paymentRecorder.recordCompleted(orderId, paymentKey, amount, result.approvedAtAsLocalDateTime());
    }

    /** 결제 실패·취소 복귀 처리. 실패 결제를 ABORTED로 저장하고 PaymentFailed를 발행한다. */
    public Payment fail(final Long orderId) {
        final int amount = resolveExpectedAmount(orderId);
        return paymentRecorder.recordAborted(orderId, amount);
    }

    private int resolveExpectedAmount(final Long orderId) {
        final Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        final Listing listing = listingRepository.findByIdAndDeletedAtIsNull(order.getListingId())
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND));
        return listing.getTotalPrice();
    }
}
