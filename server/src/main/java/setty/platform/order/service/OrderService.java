package setty.platform.order.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import setty.common.OrderRequested;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.platform.listing.application.ListingService;
import setty.platform.listing.application.ListingView;
import setty.platform.listing.domain.Listing;
import setty.platform.listing.repository.ListingRepository;
import setty.platform.member.domain.Member;
import setty.platform.member.repository.MemberRepository;
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

    public OrderService(
            final OrderRepository orderRepository,
            final ListingService listingService,
            final ListingRepository listingRepository,
            final MemberRepository memberRepository,
            final ApplicationEventPublisher eventPublisher
    ) {
        this.orderRepository = orderRepository;
        this.listingService = listingService;
        this.listingRepository = listingRepository;
        this.memberRepository = memberRepository;
        this.eventPublisher = eventPublisher;
    }

    // 결제 대기 주문 생성 — 결제 전이므로 OrderRequested(배차 요청)를 발행하지 않는다.
    @Transactional
    public Order pending(final OrderCreateRequest request, final Member buyer) {
        if (orderRepository.existsByListingId(request.listingId())) {
            throw new BusinessException(ErrorCode.ALREADY_ORDERED);
        }

        listingService.registerPurchaseRequest(request.listingId(), buyer.getId());

        try {
            return orderRepository.saveAndFlush(Order.pending(request.listingId(), buyer.getId()));
        } catch (final DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.ALREADY_ORDERED);
        }
    }

    @Transactional
    public Order create(final OrderCreateRequest request, final Member buyer) {
        if (orderRepository.existsByListingId(request.listingId())) {
            throw new BusinessException(ErrorCode.ALREADY_ORDERED);
        }

        final ListingView.PurchaseInfo purchaseInfo =
                listingService.registerPurchaseRequest(request.listingId(), buyer.getId());
        final Member seller = memberRepository.findById(purchaseInfo.sellerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));

        final Order order;
        try {
            order = orderRepository.saveAndFlush(new Order(request.listingId(), buyer.getId()));
        } catch (final DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.ALREADY_ORDERED);
        }

        eventPublisher.publishEvent(new OrderRequested(
                order.getId(),
                purchaseInfo.title(),
                purchaseInfo.category().name(),
                seller.getAddress(),
                buyer.getAddress(),
                purchaseInfo.deliveryFee(),
                seller.getPhoneNumber(),
                buyer.getPhoneNumber()
        ));

        return order;
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
