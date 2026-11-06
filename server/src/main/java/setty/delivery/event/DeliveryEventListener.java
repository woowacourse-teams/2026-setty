package setty.delivery.event;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import setty.address.AddressService;
import setty.common.OrderCancellationRequested;
import setty.common.OrderConfirmed;
import setty.delivery.application.DeliveryLifecycleService;
import setty.delivery.application.RegisterDeliveryService;
import setty.delivery.domain.GridCell;
import setty.delivery.domain.OrderId;
import setty.delivery.domain.delivery.DeliveryPoint;
import setty.delivery.domain.delivery.DeliveryRoute;
import setty.delivery.domain.delivery.EstimatedDeliveryFee;
import setty.delivery.domain.delivery.FurnitureInfo;

/**
 * 다른 컨텍스트에서 Delivery로 들어오는 이벤트의 단일 진입 경계.
 * 외부 계약(common) 이벤트를 도메인 값으로 번역해 코어를 wire 스키마·transport에서 격리한다.
 *
 * <p>발행한 쪽 트랜잭션이 커밋된 뒤 배송의 새 트랜잭션에서 처리한다. 배송 처리 실패는 발행한 쪽을 롤백하지 않는다.
 * 예외를 삼키지 않아야 실패한 이벤트가 발행 기록(EVENT_PUBLICATION)에 남아 재발행된다.
 */
@Component
@RequiredArgsConstructor
public class DeliveryEventListener {

    private final RegisterDeliveryService registerDeliveryService;
    private final DeliveryLifecycleService deliveryLifecycleService;
    private final AddressService addressService;

    @ApplicationModuleListener
    public void handle(final OrderConfirmed event) {
        registerDeliveryService.register(
                OrderId.from(event.orderId()),
                FurnitureInfo.of(event.itemName(), event.category()),
                DeliveryRoute.of(
                        DeliveryPoint.pickup(event.pickupAddress(), event.pickupPhoneNumber(),
                                event.pickupDongCode(), event.pickupZipCode(),
                                cellOf(event.pickupAddressManagementNumber())),
                        DeliveryPoint.destination(event.deliveryAddress(), event.deliveryPhoneNumber(),
                                event.deliveryDongCode(), event.deliveryZipCode(),
                                cellOf(event.deliveryAddressManagementNumber()))
                ),
                EstimatedDeliveryFee.from(event.deliveryFee()),
                Instant.now()
        );
    }

    // 묶음 배송 후보를 고르는 250m 칸. 주문 때 건물의 주소 기준표 좌표로 계산하고 좌표 자체는 남기지 않는다.
    // 관리번호나 좌표가 없으면 NULL이고 그 요청은 묶음 후보에서 빠진다.
    private Integer cellOf(final String managementNumber) {
        return addressService.findByManagementNumber(managementNumber)
                .filter(address -> address.latitude() != null && address.longitude() != null)
                .flatMap(address -> GridCell.locate(address.latitude().doubleValue(), address.longitude().doubleValue()))
                .map(GridCell::code)
                .orElse(null);
    }

    @ApplicationModuleListener
    public void handle(final OrderCancellationRequested event) {
        deliveryLifecycleService.cancel(
                OrderId.from(event.orderId()),
                event.cancellationRequestId(),
                Instant.now()
        );
    }
}
