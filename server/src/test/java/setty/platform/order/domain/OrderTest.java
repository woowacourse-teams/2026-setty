package setty.platform.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;
import setty.common.DeliveryStatus;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

class OrderTest {

    @Test
    void 결제_대기_주문은_PENDING으로_생성된다() {
        final Instant pendingExpiresAt = Instant.parse("2026-09-02T05:10:00Z");

        final Order order = Order.pending(1L, 2L, pendingExpiresAt);

        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(order.getPendingExpiresAt()).isEqualTo(pendingExpiresAt);
    }

    @Test
    void PENDING_주문은_만료_시각부터_만료할_수_있다() {
        final Instant pendingExpiresAt = Instant.parse("2026-09-02T05:10:00Z");
        final Order order = Order.pending(1L, 2L, pendingExpiresAt);

        assertThat(order.canExpire(pendingExpiresAt.minusNanos(1))).isFalse();
        assertThat(order.canExpire(pendingExpiresAt)).isTrue();
        assertThat(order.canExpire(pendingExpiresAt.plusSeconds(1))).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = DeliveryStatus.class, names = {"REQUESTED", "ACCEPTED", "PICKED_UP", "DELIVERED"})
    void PENDING이_아닌_주문은_만료_시각이_지나도_만료할_수_없다(final DeliveryStatus deliveryStatus) {
        final Instant pendingExpiresAt = Instant.parse("2026-09-02T05:10:00Z");
        final Order order = Order.pending(1L, 2L, pendingExpiresAt);
        ReflectionTestUtils.setField(order, "deliveryStatus", deliveryStatus);

        assertThat(order.canExpire(pendingExpiresAt.plusSeconds(1))).isFalse();
    }

    @Test
    void 결제_대기_주문에_배송_이벤트가_오면_거부된다() {
        final Order order = Order.pending(1L, 2L);

        assertThatThrownBy(() -> order.syncDeliveryStatus(DeliveryStatus.ACCEPTED))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_DELIVERY_STATUS_MISMATCH);
        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
    }

    @Test
    void 순방향_전이는_순서대로_반영된다() {
        final Order order = new Order(1L, 2L);

        order.syncDeliveryStatus(DeliveryStatus.ACCEPTED);
        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.ACCEPTED);
        order.syncDeliveryStatus(DeliveryStatus.PICKED_UP);
        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.PICKED_UP);
        order.syncDeliveryStatus(DeliveryStatus.DELIVERED);
        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
    }

    @Test
    void 중간_단계를_건너뛴_전이는_거부된다() {
        final Order order = new Order(1L, 2L);

        assertThatThrownBy(() -> order.syncDeliveryStatus(DeliveryStatus.DELIVERED))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_DELIVERY_STATUS_MISMATCH);
        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.REQUESTED);
    }

    @Test
    void 같은_상태_중복_이벤트는_무시된다() {
        final Order order = new Order(1L, 2L);
        order.syncDeliveryStatus(DeliveryStatus.ACCEPTED);

        order.syncDeliveryStatus(DeliveryStatus.ACCEPTED);

        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.ACCEPTED);
    }

    @Test
    void 역행_이벤트는_거부된다() {
        final Order order = new Order(1L, 2L);
        order.syncDeliveryStatus(DeliveryStatus.ACCEPTED);
        order.syncDeliveryStatus(DeliveryStatus.PICKED_UP);

        assertThatThrownBy(() -> order.syncDeliveryStatus(DeliveryStatus.ACCEPTED))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_DELIVERY_STATUS_MISMATCH);
        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.PICKED_UP);
    }

    @Test
    void null_상태는_거부된다() {
        final Order order = new Order(1L, 2L);

        assertThatThrownBy(() -> order.syncDeliveryStatus(null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void 결제대기_주문은_배송요청으로_전환된다() {
        final Order order = new Order(1L, 2L);
        ReflectionTestUtils.setField(order, "deliveryStatus", DeliveryStatus.PENDING);

        assertThat(order.requestDelivery()).isTrue();
        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.REQUESTED);
        assertThat(order.requestDelivery()).isFalse();
    }
}
