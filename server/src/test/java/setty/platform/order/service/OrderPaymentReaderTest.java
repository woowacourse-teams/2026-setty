package setty.platform.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.platform.listing.domain.Listing;
import setty.platform.listing.repository.ListingRepository;
import setty.platform.order.domain.Order;
import setty.platform.order.repository.OrderRepository;

@ExtendWith(MockitoExtension.class)
class OrderPaymentReaderTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ListingRepository listingRepository;

    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");

    private OrderPaymentReader reader;

    @BeforeEach
    void setUp() {
        reader = new OrderPaymentReader(orderRepository, listingRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void 주문의_매물_총액을_반환한다() {
        final Order order = Order.pending(11L, 22L, NOW.plusSeconds(60));
        final Listing listing = mock(Listing.class);
        when(orderRepository.findById(33L)).thenReturn(Optional.of(order));
        when(listingRepository.findByIdAndDeletedAtIsNull(11L)).thenReturn(Optional.of(listing));
        when(listing.getTotalPrice()).thenReturn(160_000);

        assertThat(reader.payableAmount(33L)).isEqualTo(160_000);
    }

    @Test
    void 삭제된_매물은_금액을_제공하지_않는다() {
        when(orderRepository.findById(33L)).thenReturn(Optional.of(Order.pending(11L, 22L, NOW.plusSeconds(60))));
        when(listingRepository.findByIdAndDeletedAtIsNull(11L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reader.payableAmount(33L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.LISTING_NOT_FOUND);
    }

    @Test
    void 만료_시각이_지난_PENDING_주문은_결제할_수_없다() {
        when(orderRepository.findById(33L)).thenReturn(Optional.of(Order.pending(11L, 22L, NOW)));

        assertThatThrownBy(() -> reader.payableAmount(33L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_NOT_PAYABLE);
    }

    @Test
    void 다른_구매자는_주문_존재_여부를_알_수_없다() {
        when(orderRepository.findByIdAndBuyerId(33L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reader.verifyBuyer(33L, 99L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }
}
