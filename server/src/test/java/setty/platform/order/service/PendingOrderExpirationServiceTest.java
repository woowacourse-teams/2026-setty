package setty.platform.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import setty.common.DeliveryStatus;
import setty.platform.listing.application.ListingService;
import setty.platform.order.domain.Order;
import setty.platform.order.repository.OrderRepository;

@ExtendWith(MockitoExtension.class)
class PendingOrderExpirationServiceTest {

    private static final long ORDER_ID = 101L;
    private static final long LISTING_ID = 201L;
    private static final long BUYER_ID = 301L;
    private static final Instant EXPIRES_AT = Instant.parse("2026-09-02T05:10:00Z");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ListingService listingService;

    @InjectMocks
    private PendingOrderExpirationService expirationService;

    @Test
    void 만료된_PENDING_주문은_매물_구매_요청을_해제하고_삭제한다() {
        final Order order = pendingOrder();
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

        final boolean expired = expirationService.expire(ORDER_ID, EXPIRES_AT);

        assertThat(expired).isTrue();
        final InOrder inOrder = inOrder(orderRepository, listingService);
        inOrder.verify(orderRepository).findByIdForUpdate(ORDER_ID);
        inOrder.verify(orderRepository).countPaymentReferences(ORDER_ID);
        inOrder.verify(listingService).releasePurchaseRequestForExpiredPendingOrder(LISTING_ID);
        inOrder.verify(orderRepository).delete(order);
    }

    @Test
    void 아직_만료되지_않은_PENDING_주문은_유지한다() {
        final Order order = pendingOrder();
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

        final boolean expired = expirationService.expire(ORDER_ID, EXPIRES_AT.minusNanos(1));

        assertThat(expired).isFalse();
        verify(orderRepository, never()).countPaymentReferences(ORDER_ID);
        verify(orderRepository, never()).delete(order);
        verifyNoInteractions(listingService);
    }

    @ParameterizedTest
    @EnumSource(value = DeliveryStatus.class, names = {"REQUESTED", "ACCEPTED", "PICKED_UP", "DELIVERED"})
    void REQUESTED_이상_주문은_유지한다(final DeliveryStatus deliveryStatus) {
        final Order order = pendingOrder();
        ReflectionTestUtils.setField(order, "deliveryStatus", deliveryStatus);
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

        final boolean expired = expirationService.expire(ORDER_ID, EXPIRES_AT.plusSeconds(1));

        assertThat(expired).isFalse();
        verify(orderRepository, never()).countPaymentReferences(ORDER_ID);
        verify(orderRepository, never()).delete(order);
        verifyNoInteractions(listingService);
    }

    @Test
    void payment_참조가_있는_PENDING_주문은_유지한다() {
        final Order order = pendingOrder();
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.countPaymentReferences(ORDER_ID)).thenReturn(1L);

        final boolean expired = expirationService.expire(ORDER_ID, EXPIRES_AT.plusSeconds(1));

        assertThat(expired).isFalse();
        verify(orderRepository, never()).delete(order);
        verifyNoInteractions(listingService);
    }

    @Test
    void 잠금_대기_중_REQUESTED로_전이된_주문은_삭제하지_않는다() throws Exception {
        final Order order = pendingOrder();
        final CountDownLatch expirationLookupStarted = new CountDownLatch(1);
        final CountDownLatch paymentCompleted = new CountDownLatch(1);
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenAnswer(invocation -> {
            expirationLookupStarted.countDown();
            if (!paymentCompleted.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("결제 완료 상태 전이를 기다리지 못했습니다");
            }
            return Optional.of(order);
        });

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            final Future<Boolean> expiration = executor.submit(
                    () -> expirationService.expire(ORDER_ID, EXPIRES_AT.plusSeconds(1))
            );
            assertThat(expirationLookupStarted.await(5, TimeUnit.SECONDS)).isTrue();

            order.requestDelivery();
            paymentCompleted.countDown();

            assertThat(expiration.get(5, TimeUnit.SECONDS)).isFalse();
        }

        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.REQUESTED);
        verify(orderRepository, never()).delete(order);
        verifyNoInteractions(listingService);
    }

    private static Order pendingOrder() {
        final Order order = Order.pending(LISTING_ID, BUYER_ID, EXPIRES_AT);
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        return order;
    }
}
