package setty.platform.listing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import setty.common.DeliveryAccepted;
import setty.common.DeliveryDelivered;
import setty.common.OrderCancelled;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.platform.order.domain.Order;
import setty.platform.order.repository.OrderRepository;

@ExtendWith(MockitoExtension.class)
class ListingDeliveryEventListenerTest {

    private static final long ORDER_ID = 101L;
    private static final long LISTING_ID = 301L;
    private static final Instant CHANGED_AT = Instant.parse("2026-08-27T01:00:00Z");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ListingService listingService;

    @InjectMocks
    private ListingDeliveryEventListener listener;

    @Test
    void 배송_수락_사실을_받으면_매물을_배송_예약한다() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order()));

        listener.onDeliveryAccepted(new DeliveryAccepted(201L, ORDER_ID, CHANGED_AT));

        verify(listingService).reserveForDelivery(LISTING_ID);
    }

    @Test
    void 배송_완료_사실을_받으면_매물_판매를_완료한다() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order()));

        listener.onDeliveryDelivered(new DeliveryDelivered(201L, ORDER_ID, CHANGED_AT, 301L, 10_000));

        verify(listingService).completeSale(LISTING_ID);
    }

    @Test
    void 주문_취소_확정_사실을_받으면_매물_선점을_해제한다() {
        listener.onOrderCancelled(new OrderCancelled(ORDER_ID, LISTING_ID, "cancel-request-1", CHANGED_AT));

        verify(listingService).releasePurchaseRequest(LISTING_ID);
        verifyNoInteractions(orderRepository);
    }

    @Test
    void 잘못된_배송_사실은_주문을_조회하기_전에_거부한다() {
        assertThatThrownBy(() -> listener.onDeliveryAccepted(new DeliveryAccepted(null, ORDER_ID, CHANGED_AT)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_REQUEST)
                );

        verifyNoInteractions(orderRepository, listingService);
    }

    private static Order order() {
        return new Order(LISTING_ID, 401L);
    }
}
