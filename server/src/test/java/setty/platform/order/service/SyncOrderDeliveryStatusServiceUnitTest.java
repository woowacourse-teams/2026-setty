package setty.platform.order.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import setty.common.DeliveryStatus;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.platform.order.repository.OrderRepository;

@ExtendWith(MockitoExtension.class)
class SyncOrderDeliveryStatusServiceUnitTest {

    private static final Instant CHANGED_AT = Instant.parse("2026-08-31T01:00:00Z");

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private SyncOrderDeliveryStatusService service;

    @ParameterizedTest
    @MethodSource("invalidValues")
    void 잘못된_배송_값은_주문을_조회하기_전에_거부한다(
            final Long deliveryId,
            final Long orderId,
            final Instant changedAt,
            final DeliveryStatus status
    ) {
        assertThatThrownBy(() -> service.sync(deliveryId, orderId, changedAt, status))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);

        verifyNoInteractions(orderRepository);
    }

    private static Stream<Arguments> invalidValues() {
        return Stream.of(
                Arguments.of(null, 1L, CHANGED_AT, DeliveryStatus.ACCEPTED),
                Arguments.of(0L, 1L, CHANGED_AT, DeliveryStatus.ACCEPTED),
                Arguments.of(-1L, 1L, CHANGED_AT, DeliveryStatus.ACCEPTED),
                Arguments.of(1L, null, CHANGED_AT, DeliveryStatus.ACCEPTED),
                Arguments.of(1L, 0L, CHANGED_AT, DeliveryStatus.ACCEPTED),
                Arguments.of(1L, -1L, CHANGED_AT, DeliveryStatus.ACCEPTED),
                Arguments.of(1L, 1L, null, DeliveryStatus.ACCEPTED),
                Arguments.of(1L, 1L, CHANGED_AT, null)
        );
    }
}
