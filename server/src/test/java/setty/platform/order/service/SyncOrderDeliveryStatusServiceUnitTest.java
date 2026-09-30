package setty.platform.order.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import setty.common.DeliveryAccepted;
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
    @MethodSource("invalidEvents")
    void 잘못된_이벤트는_주문을_조회하기_전에_거부한다(final DeliveryAccepted event) {
        assertThatThrownBy(() -> service.sync(event))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);

        verifyNoInteractions(orderRepository);
    }

    private static Stream<DeliveryAccepted> invalidEvents() {
        return Stream.of(
                null,
                new DeliveryAccepted(null, 1L, CHANGED_AT),
                new DeliveryAccepted(0L, 1L, CHANGED_AT),
                new DeliveryAccepted(-1L, 1L, CHANGED_AT),
                new DeliveryAccepted(1L, null, CHANGED_AT),
                new DeliveryAccepted(1L, 0L, CHANGED_AT),
                new DeliveryAccepted(1L, -1L, CHANGED_AT),
                new DeliveryAccepted(1L, 1L, null)
        );
    }
}
