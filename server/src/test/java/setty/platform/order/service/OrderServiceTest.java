package setty.platform.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import setty.platform.listing.domain.Listing;
import setty.platform.listing.domain.ListingCategory;
import setty.platform.listing.repository.ListingRepository;
import setty.platform.member.domain.Member;
import setty.platform.member.repository.MemberRepository;
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

    @InjectMocks
    private OrderService orderService;

    @Test
    void 결제완료_주문으로_배송요청_이벤트를_발행한다() {
        final Order order = order();
        final Listing listing = listing();
        final Member seller = member("판매자 주소", "010-0000-0001");
        final Member buyer = member("구매자 주소", "010-0000-0002");
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
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
    }

    @Test
    void 이미_배송요청_상태인_주문에는_이벤트를_다시_발행하지_않는다() {
        final Order order = new Order(LISTING_ID, BUYER_ID);
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

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
