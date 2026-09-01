package setty.platform.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import setty.common.DeliveryStatus;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

class OrderTest {

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
