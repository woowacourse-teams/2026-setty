package setty.platform.order.service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.common.DeliveryStatus;
import setty.common.OrderRequested;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.platform.listing.application.ListingService;
import setty.platform.listing.domain.Listing;
import setty.platform.listing.repository.ListingRepository;
import setty.platform.member.domain.Member;
import setty.platform.member.repository.MemberRepository;
import setty.platform.order.config.PendingOrderExpirationProperties;
import setty.platform.order.controller.dto.MyOrderResponse;
import setty.platform.order.controller.dto.OrderCreateRequest;
import setty.platform.order.domain.Order;
import setty.platform.order.repository.OrderRepository;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ListingService listingService;
    private final ListingRepository listingRepository;
    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final PendingOrderExpirationProperties pendingOrderExpirationProperties;

    public OrderService(
            final OrderRepository orderRepository,
            final ListingService listingService,
            final ListingRepository listingRepository,
            final MemberRepository memberRepository,
            final ApplicationEventPublisher eventPublisher,
            final Clock clock,
            final PendingOrderExpirationProperties pendingOrderExpirationProperties
    ) {
        this.orderRepository = orderRepository;
        this.listingService = listingService;
        this.listingRepository = listingRepository;
        this.memberRepository = memberRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.pendingOrderExpirationProperties = pendingOrderExpirationProperties;
    }

    // 결제 대기 주문 생성 — 결제 전이므로 OrderRequested(배차 요청)를 발행하지 않는다.
    @Transactional
    public Order pending(final OrderCreateRequest request, final Member buyer) {
        if (orderRepository.existsByListingId(request.listingId())) {
            throw new BusinessException(ErrorCode.ALREADY_ORDERED);
        }

        listingService.registerPurchaseRequest(request.listingId(), buyer.getId());

        try {
            return orderRepository.saveAndFlush(Order.pending(
                    request.listingId(),
                    buyer.getId(),
                    clock.instant().plus(pendingOrderExpirationProperties.timeout())
            ));
        } catch (final DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.ALREADY_ORDERED);
        }
    }

    @Transactional
    public void publishOrderRequested(final Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        final Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.requestDelivery()) {
            return;
        }

        final Listing listing = listingRepository.findById(order.getListingId())
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND));
        final Member seller = memberRepository.findById(listing.getSellerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        final Member buyer = memberRepository.findById(order.getBuyerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));

        eventPublisher.publishEvent(new OrderRequested(
                order.getId(),
                listing.getTitle(),
                listing.getCategory().name(),
                seller.getAddress(),
                buyer.getAddress(),
                listing.getDeliveryFee(),
                seller.getPhoneNumber(),
                buyer.getPhoneNumber()
        ));
    }

    /**
     * 결제 실패(PaymentFailed) 보상 — PENDING 주문을 삭제하고 매물 선점을 해제해 다시 구매 가능하게 한다.
     * 주문이 없거나(중복 실패 복귀) 이미 REQUESTED 이상이면(결제 완료된 주문 보호) 조용히 무시한다.
     */
    @Transactional
    public void cancelPending(final Long orderId) {
        if (orderId == null || orderId <= 0) {
            return;
        }

        final Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || order.getDeliveryStatus() != DeliveryStatus.PENDING) {
            return;
        }

        listingService.releasePurchaseRequest(order.getListingId());
        orderRepository.delete(order);
    }

    @Transactional(readOnly = true)
    public List<MyOrderResponse> findMyOrders(final Long buyerId) {
        final List<Order> orders = orderRepository.findAllByBuyerIdOrderByIdDesc(buyerId);
        final List<Long> listingIds = orders.stream().map(Order::getListingId).toList();
        final Map<Long, Listing> listings = listingRepository.findAllById(listingIds).stream()
                .collect(Collectors.toMap(Listing::getId, Function.identity()));

        return orders.stream()
                .map(order -> MyOrderResponse.of(order, listings.get(order.getListingId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public MyOrderResponse findMyOrder(final Long orderId, final Long buyerId) {
        final Order order = orderRepository.findByIdAndBuyerId(orderId, buyerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        final Listing listing = listingRepository.findById(order.getListingId()).orElse(null);
        return MyOrderResponse.of(order, listing);
    }
}
