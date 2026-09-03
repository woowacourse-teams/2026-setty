package setty.platform.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import setty.common.DeliveryStatus;
import setty.common.OrderRequested;
import setty.platform.listing.application.ListingService;
import setty.platform.listing.application.ListingView;
import setty.platform.listing.domain.ConditionGrade;
import setty.platform.listing.domain.Listing;
import setty.platform.listing.domain.ListingCategory;
import setty.platform.listing.repository.ListingRepository;
import setty.platform.member.domain.Member;
import setty.platform.member.repository.MemberRepository;
import setty.platform.order.config.PendingOrderExpirationProperties;
import setty.platform.order.controller.dto.MyOrderResponse;
import setty.platform.order.controller.dto.OrderCreateRequest;
import setty.platform.order.domain.Order;
import setty.platform.order.repository.OrderRepository;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final long ORDER_ID = 101L;
    private static final long LISTING_ID = 201L;
    private static final long BUYER_ID = 301L;
    private static final long SELLER_ID = 401L;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ListingService listingService;

    @Mock
    private ListingRepository listingRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private Clock clock;

    @Mock
    private PendingOrderExpirationProperties pendingOrderExpirationProperties;

    @InjectMocks
    private OrderService orderService;

    @Test
    void 결제_대기_주문에_설정된_만료_시간을_저장한다() {
        final Instant createdAt = Instant.parse("2026-09-02T05:00:00Z");
        final Duration timeout = Duration.ofMinutes(3);
        final Member buyer = mock(Member.class);
        when(buyer.getId()).thenReturn(BUYER_ID);
        when(clock.instant()).thenReturn(createdAt);
        when(pendingOrderExpirationProperties.timeout()).thenReturn(timeout);
        when(orderRepository.saveAndFlush(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        final Order order = orderService.pending(new OrderCreateRequest(LISTING_ID), buyer);

        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(order.getPendingExpiresAt()).isEqualTo(createdAt.plus(timeout));
        verify(listingService).registerPurchaseRequest(LISTING_ID, BUYER_ID);
    }

    @Test
    void 결제완료_주문으로_배송요청_이벤트를_발행한다() {
        final Order order = order();
        final Listing listing = listing();
        final Member seller = member("판매자 주소", "010-0000-0001");
        final Member buyer = member("구매자 주소", "010-0000-0002");
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));
        when(memberRepository.findById(SELLER_ID)).thenReturn(Optional.of(seller));
        when(memberRepository.findById(BUYER_ID)).thenReturn(Optional.of(buyer));

        orderService.publishOrderRequested(ORDER_ID);

        final ArgumentCaptor<OrderRequested> eventCaptor = ArgumentCaptor.forClass(OrderRequested.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.REQUESTED);
        assertThat(eventCaptor.getValue()).isEqualTo(new OrderRequested(
                ORDER_ID,
                "가상 책상",
                ListingCategory.DESK.name(),
                "판매자 주소",
                "구매자 주소",
                10_000,
                "010-0000-0001",
                "010-0000-0002"
        ));
        verify(orderRepository).findByIdForUpdate(ORDER_ID);
    }

    @Test
    void 이미_배송요청_상태인_주문에는_이벤트를_다시_발행하지_않는다() {
        final Order order = new Order(LISTING_ID, BUYER_ID);
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

        orderService.publishOrderRequested(ORDER_ID);

        verifyNoInteractions(listingRepository, memberRepository, eventPublisher);
    }

    @Test
    void 결제대기_주문을_취소하면_주문이_삭제되고_선점이_해제된다() {
        final Order order = order();
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

        orderService.cancelPending(ORDER_ID);

        verify(listingService).releasePurchaseRequest(LISTING_ID);
        verify(orderRepository).delete(order);
    }

    @Test
    void 결제대기가_아닌_주문은_취소_요청을_무시한다() {
        final Order order = new Order(LISTING_ID, BUYER_ID);
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

        orderService.cancelPending(ORDER_ID);

        verifyNoInteractions(listingService);
        verify(orderRepository, never()).delete(order);
    }

    @Test
    void 존재하지_않는_주문의_취소_요청은_무시한다() {
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.empty());

        orderService.cancelPending(ORDER_ID);

        verifyNoInteractions(listingService);
    }

    @Test
    void 내_주문_목록에_매물_썸네일을_포함한다() {
        final Order order = new Order(LISTING_ID, BUYER_ID);
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        final ListingView.Summary listing = new ListingView.Summary(
                LISTING_ID,
                "가상 책상",
                "https://example.com/listings/desk.jpg",
                100_000,
                10_000,
                110_000,
                ListingCategory.DESK,
                ConditionGrade.A,
                new ListingView.Dimensions(120, 60, 75),
                Instant.parse("2026-09-03T00:00:00Z")
        );
        when(orderRepository.findAllByBuyerIdOrderByIdDesc(BUYER_ID)).thenReturn(List.of(order));
        when(listingService.findSummaries(List.of(LISTING_ID))).thenReturn(List.of(listing));

        final List<MyOrderResponse> responses = orderService.findMyOrders(BUYER_ID);

        assertThat(responses).singleElement().satisfies(response -> {
            assertThat(response.listing().id()).isEqualTo(LISTING_ID);
            assertThat(response.listing().name()).isEqualTo("가상 책상");
            assertThat(response.listing().thumbnailUrl()).isEqualTo("https://example.com/listings/desk.jpg");
        });
    }

    private static Order order() {
        final Order order = new Order(LISTING_ID, BUYER_ID);
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        ReflectionTestUtils.setField(order, "deliveryStatus", DeliveryStatus.PENDING);
        return order;
    }

    private static Listing listing() {
        final Listing listing = mock(Listing.class);
        when(listing.getSellerId()).thenReturn(SELLER_ID);
        when(listing.getTitle()).thenReturn("가상 책상");
        when(listing.getCategory()).thenReturn(ListingCategory.DESK);
        when(listing.getDeliveryFee()).thenReturn(10_000);
        return listing;
    }

    private static Member member(final String address, final String phoneNumber) {
        final Member member = mock(Member.class);
        when(member.getAddress()).thenReturn(address);
        when(member.getPhoneNumber()).thenReturn(phoneNumber);
        return member;
    }
}
