package setty.delivery.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import setty.common.DeliveryStatus;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

class OrderDeliveryStateTest {

    @Test
    void synchronizesOnlyFromExpectedPreviousStatus() {
        final OrderDeliveryState state = new OrderDeliveryState(new OrderId(1L), DeliveryStatus.REQUESTED);

        assertThat(state.synchronizeTo(DeliveryStatus.ACCEPTED)).isTrue();
        assertThat(state.getStatus()).isEqualTo(DeliveryStatus.ACCEPTED);
        assertThat(state.synchronizeTo(DeliveryStatus.PICKED_UP)).isTrue();
        assertThat(state.getStatus()).isEqualTo(DeliveryStatus.PICKED_UP);
        assertThat(state.synchronizeTo(DeliveryStatus.DELIVERED)).isTrue();
        assertThat(state.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
    }

    @Test
    void sameStatusIsIdempotent() {
        final OrderDeliveryState state = new OrderDeliveryState(new OrderId(1L), DeliveryStatus.ACCEPTED);

        assertThat(state.synchronizeTo(DeliveryStatus.ACCEPTED)).isFalse();
        assertThat(state.getStatus()).isEqualTo(DeliveryStatus.ACCEPTED);
    }

    @Test
    void mismatchedPreviousStatusIsRejected() {
        final OrderDeliveryState state = new OrderDeliveryState(new OrderId(1L), DeliveryStatus.DELIVERED);

        assertThatThrownBy(() -> state.synchronizeTo(DeliveryStatus.PICKED_UP))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.ORDER_DELIVERY_STATUS_MISMATCH)
                );
    }
}
