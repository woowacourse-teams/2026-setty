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
import setty.common.DeliveryStatusChanged;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.platform.order.domain.Order;
import setty.platform.order.repository.OrderRepository;

@ExtendWith(MockitoExtension.class)
class ListingDeliveryStatusChangedListenerTest {

    private static final long ORDER_ID = 101L;
    private static final long LISTING_ID = 301L;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ListingService listingService;

    @InjectMocks
    private ListingDeliveryStatusChangedListener listener;

    @Test
    void reservesListingWhenDeliveryIsAccepted() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order()));

        listener.handle(event("ACCEPTED"));

        verify(listingService).reserveForDelivery(LISTING_ID);
    }

    @Test
    void doesNotChangeListingWhenDeliveryIsPickedUp() {
        listener.handle(event("PICKED_UP"));

        verifyNoInteractions(orderRepository, listingService);
    }

    @Test
    void completesListingSaleWhenDeliveryIsDelivered() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order()));

        listener.handle(event("DELIVERED"));

        verify(listingService).completeSale(LISTING_ID);
    }

    @Test
    void rejectsUnsupportedDeliveryStatus() {
        assertThatThrownBy(() -> listener.handle(event("REQUESTED")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST)
                );

        verifyNoInteractions(orderRepository, listingService);
    }

    private static Order order() {
        return new Order(LISTING_ID, 401L);
    }

    private static DeliveryStatusChanged event(final String status) {
        return new DeliveryStatusChanged(
                201L,
                ORDER_ID,
                status,
                Instant.parse("2026-08-27T01:00:00Z")
        );
    }
}
