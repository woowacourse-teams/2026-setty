package setty.platform.order.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.common.OrderCancellationRequested;
import setty.common.OrderCancelled;
import setty.common.OrderConfirmed;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.platform.listing.application.ListingService;
import setty.platform.listing.application.ListingView;
import setty.platform.listing.domain.Listing;
import setty.platform.listing.repository.ListingRepository;
import setty.platform.member.domain.Member;
import setty.platform.member.repository.MemberRepository;
import setty.platform.order.config.PendingOrderExpirationProperties;
import setty.platform.order.controller.dto.MyOrderResponse;
import setty.platform.order.controller.dto.OrderCancellationResponse;
import setty.platform.order.controller.dto.OrderCreateRequest;
import setty.platform.order.domain.Order;
import setty.platform.order.domain.OrderPlace;
import setty.platform.order.domain.OrderStatus;
import setty.platform.order.repository.OrderRepository;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

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

    // 결제 대기 주문 생성 — 결제 전이므로 OrderConfirmed(배차 요청)를 발행하지 않는다.
    @Transactional
    public Order pending(final OrderCreateRequest request, final Member buyer) {
        if (orderRepository.existsByListingIdAndOrderStatusNotIn(
                request.listingId(), List.of(OrderStatus.EXPIRED, OrderStatus.CANCELLED))) {
            throw new BusinessException(ErrorCode.ALREADY_ORDERED);
        }

        final ListingView.PurchaseInfo purchase =
                listingService.registerPurchaseRequest(request.listingId(), buyer.getId(), buyer.getDongCode());
        final Member seller = memberRepository.findById(purchase.sellerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));

        try {
            return orderRepository.saveAndFlush(Order.pending(
                    request.listingId(),
                    buyer.getId(),
                    clock.instant().plus(pendingOrderExpirationProperties.timeout()),
                    purchase.deliveryFee(),
                    new OrderPlace(seller.getAddress(), seller.getPhoneNumber(), purchase.dongCode(),
                            purchase.zipCode(), managementNumberOf(seller)),
                    new OrderPlace(buyer.getAddress(), buyer.getPhoneNumber(), buyer.getDongCode(),
                            zipCodeOf(buyer), managementNumberOf(buyer))
            ));
        } catch (final DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.ALREADY_ORDERED);
        }
    }

    // 표준 주소는 해석에 성공한 회원만 값이 있다. 출발지 우편번호는 회원이 아니라 매물 구역에서 온다.
    private static String zipCodeOf(final Member member) {
        return member.getResolvedAddress() == null ? null : member.getResolvedAddress().getZipCode();
    }

    private static String managementNumberOf(final Member member) {
        return member.getResolvedAddress() == null ? null : member.getResolvedAddress().getManagementNumber();
    }

    @Transactional
    public void publishOrderConfirmed(final Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        final Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (order.getOrderStatus() == OrderStatus.EXPIRED) {
            log.error("만료된 주문에 결제 완료가 도착했습니다. 수동 환불 확인이 필요합니다. orderId={}", orderId);
            return;
        }
        if (!order.requestDelivery()) {
            return;
        }

        final Listing listing = listingRepository.findById(order.getListingId())
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND));
        // 출발지·배송지는 주문 시점 값을 쓴다(docs/policy/delivery.md). 도입 전 주문만 지금 회원 정보로 대신한다.
        final OrderPlace pickup = order.getPickup() != null ? order.getPickup() : currentPickup(listing);
        final OrderPlace destination = order.getDestination() != null
                ? order.getDestination() : currentDestination(order.getBuyerId());
        eventPublisher.publishEvent(new OrderConfirmed(
                order.getId(),
                listing.getTitle(),
                listing.getCategory().name(),
                pickup.address(),
                destination.address(),
                order.getDeliveryFee() == null ? listing.getDeliveryFee() : order.getDeliveryFee(),
                pickup.phoneNumber(),
                destination.phoneNumber(),
                listing.getId(),
                listing.getSellerId(),
                listing.getPrice(),
                destination.dongCode(),
                pickup.dongCode(),
                pickup.zipCode(),
                destination.zipCode()
        ));
    }

    private OrderPlace currentPickup(final Listing listing) {
        final Member seller = memberRepository.findById(listing.getSellerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        return new OrderPlace(seller.getAddress(), seller.getPhoneNumber(), listing.getDongCode(), null, null);
    }

    // 배송지 저장 전 주문은 행정동도 저장하지 않았으므로 지금 회원 동을 쓰지 않는다(배송비 계산에 쓴 동이 아니다).
    private OrderPlace currentDestination(final Long buyerId) {
        final Member buyer = memberRepository.findById(buyerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        return new OrderPlace(buyer.getAddress(), buyer.getPhoneNumber(), null, null, null);
    }

    @Transactional
    public OrderCancellationResponse requestCancellation(final Long orderId, final Long buyerId) {
        if (orderId == null || orderId <= 0 || buyerId == null || buyerId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        final Order order = orderRepository.findByIdAndBuyerIdForUpdate(orderId, buyerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        final String cancellationRequestId = UUID.randomUUID().toString();
        final boolean cancelStarted = order.requestCancellation(cancellationRequestId);
        final OrderCancellationResponse response = OrderCancellationResponse.from(order);
        if (cancelStarted) {
            eventPublisher.publishEvent(new OrderCancellationRequested(
                    order.getId(),
                    order.getCancellationRequestId()
            ));
        }
        return response;
    }

    @Transactional
    public void confirmCancellation(
            final Long orderId,
            final String cancellationRequestId,
            final Instant decidedAt
    ) {
        validateCancellationResult(orderId, cancellationRequestId, decidedAt);

        final Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.confirmCancellation(cancellationRequestId)) {
            return;
        }

        eventPublisher.publishEvent(new OrderCancelled(
                order.getId(),
                order.getListingId(),
                cancellationRequestId,
                clock.instant()
        ));
    }

    @Transactional
    public void rejectCancellation(
            final Long orderId,
            final String cancellationRequestId,
            final Instant decidedAt
    ) {
        validateCancellationResult(orderId, cancellationRequestId, decidedAt);

        final Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        order.rejectCancellation(cancellationRequestId);
    }

    private void validateCancellationResult(
            final Long orderId,
            final String cancellationRequestId,
            final Instant decidedAt
    ) {
        if (orderId == null || orderId <= 0
                || cancellationRequestId == null || cancellationRequestId.isBlank()
                || decidedAt == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    @Transactional(readOnly = true)
    public List<MyOrderResponse> findMyOrders(final Long buyerId) {
        final List<Order> orders = orderRepository.findAllByBuyerIdOrderByIdDesc(buyerId);
        final List<Long> listingIds = orders.stream().map(Order::getListingId).distinct().toList();
        final Map<Long, ListingView.Summary> listings = listingService.findSummaries(listingIds, null).stream()
                .collect(Collectors.toMap(ListingView.Summary::id, Function.identity()));

        return orders.stream()
                .map(order -> MyOrderResponse.of(order, listings.get(order.getListingId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public MyOrderResponse findMyOrder(final Long orderId, final Long buyerId) {
        final Order order = orderRepository.findByIdAndBuyerId(orderId, buyerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        final ListingView.Summary listing = listingService.findSummaries(List.of(order.getListingId()), null).stream()
                .findFirst()
                .orElse(null);
        return MyOrderResponse.of(order, listing);
    }
}
