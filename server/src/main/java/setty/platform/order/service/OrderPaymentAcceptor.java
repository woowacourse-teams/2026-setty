package setty.platform.order.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.platform.PaymentAcceptance;
import setty.platform.PaymentOrderAcceptance;
import setty.platform.listing.application.ListingService;
import setty.platform.listing.domain.Listing;
import setty.platform.listing.repository.ListingRepository;
import setty.platform.order.domain.Order;
import setty.platform.order.repository.OrderRepository;

/**
 * 토스 승인을 확인한 결제에 대해 주문의 결과를 먼저 정한다(docs/policy/order.md 만료·재결제).
 *
 * <p>만료·판매자 취소와 같은 순서(매물 → 주문)로 잠가 서로 기다리다 멈추지 않게 하고, 먼저 잠근 쪽의 결과가 남는다.
 * 결제 금액 검증 때 읽은 주문·매물은 그사이 만료·취소로 바뀌었을 수 있으므로 잠근 뒤 DB 값으로 다시 읽는다.
 * 결제 트랜잭션과 따로 커밋해야 하므로 새 트랜잭션에서 실행한다.
 */
@Service
public class OrderPaymentAcceptor implements PaymentOrderAcceptance {

    private static final Logger log = LoggerFactory.getLogger(OrderPaymentAcceptor.class);

    private final OrderRepository orderRepository;
    private final ListingRepository listingRepository;
    private final ListingService listingService;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;

    public OrderPaymentAcceptor(
            final OrderRepository orderRepository,
            final ListingRepository listingRepository,
            final ListingService listingService,
            final ApplicationEventPublisher eventPublisher,
            final EntityManager entityManager
    ) {
        this.orderRepository = orderRepository;
        this.listingRepository = listingRepository;
        this.listingService = listingService;
        this.eventPublisher = eventPublisher;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentAcceptance accept(final Long orderId, final Instant confirmedAt) {
        if (orderId == null || orderId <= 0 || confirmedAt == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        final Long listingId = orderRepository.findListingIdById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        final Listing listing = listingRepository.findByIdForUpdate(listingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND));
        entityManager.refresh(listing);
        final Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        entityManager.refresh(order, LockModeType.PESSIMISTIC_WRITE);

        if (order.acceptPayment(confirmedAt)) {
            eventPublisher.publishEvent(new OrderPaid(orderId));
            return PaymentAcceptance.ofAccepted();
        }
        if (order.hasAcceptedPayment()) {
            return PaymentAcceptance.ofAccepted();
        }
        if (order.expire(confirmedAt)) {
            // 만료 스캔 전에 기한이 지난 승인이 도착했다. 만료 경로와 같이 선점을 해제하고 승인은 환불로 보낸다.
            listingService.releasePurchaseRequestForExpiredPendingOrder(listingId);
            log.warn("만료 시각이 지난 주문에 승인이 도착해 만료 처리했습니다. orderId={}", orderId);
        } else {
            log.warn("만료되거나 판매자가 취소한 주문에 승인이 도착했습니다. orderId={}, orderStatus={}",
                    orderId, order.getOrderStatus());
        }
        return PaymentAcceptance.ofRejected(order.getOrderStatus().name());
    }
}
