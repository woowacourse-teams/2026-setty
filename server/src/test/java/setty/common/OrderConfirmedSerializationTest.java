package setty.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.modulith.events.core.EventSerializer;
import setty.support.MySqlIntegrationTestSupport;

@SpringBootTest
class OrderConfirmedSerializationTest extends MySqlIntegrationTestSupport {

    // 이벤트 발행 기록(EVENT_PUBLICATION)을 저장·복원하는 직렬화기
    @Autowired
    private EventSerializer eventSerializer;

    // 배포 전에 저장된 미완료 이벤트가 재발행될 때 새 필드가 없어도 읽혀야 한다
    @Test
    void 배송지_행정동이_없는_예전_이벤트도_읽고_행정동은_null이다() {
        String legacy = """
                {"orderId":1,"itemName":"가상 책상","category":"DESK","pickupAddress":"판매자 주소",
                 "deliveryAddress":"구매자 주소","deliveryFee":10000,"pickupPhoneNumber":"010-0000-0001",
                 "deliveryPhoneNumber":"010-0000-0002","listingId":100,"sellerId":10,"itemPrice":150000}
                """;

        OrderConfirmed event = eventSerializer.deserialize(legacy, OrderConfirmed.class);

        assertThat(event.deliveryDongCode()).isNull();
        assertThat(event.pickupDongCode()).isNull();
        assertThat(event.pickupZipCode()).isNull();
        assertThat(event.deliveryZipCode()).isNull();
        assertThat(event.deliveryFee()).isEqualTo(10_000);
        assertThat(event.deliveryAddress()).isEqualTo("구매자 주소");
    }

    // 블루그린 배포 중 옛 슬롯이 새 슬롯의 이벤트를 다시 읽을 때를 흉내 낸다
    @Test
    void 모르는_필드가_더_있는_이벤트도_읽는다() {
        String newer = """
                {"orderId":1,"itemName":"가상 책상","category":"DESK","pickupAddress":"판매자 주소",
                 "deliveryAddress":"구매자 주소","deliveryFee":10000,"pickupPhoneNumber":"010-0000-0001",
                 "deliveryPhoneNumber":"010-0000-0002","listingId":100,"sellerId":10,"itemPrice":150000,
                 "deliveryDongCode":"1111053000","pickupDongCode":"1111060000","pickupZipCode":"04524",
                 "deliveryZipCode":"06236","unknownFutureField":"x"}
                """;

        OrderConfirmed event = eventSerializer.deserialize(newer, OrderConfirmed.class);

        assertThat(event.pickupZipCode()).isEqualTo("04524");
        assertThat(event.deliveryZipCode()).isEqualTo("06236");
    }

    @Test
    void 출발지와_배송지_구역을_담아_저장하고_다시_읽는다() {
        OrderConfirmed event = new OrderConfirmed(1L, "가상 책상", "DESK", "판매자 주소", "구매자 주소", 25_000,
                "010-0000-0001", "010-0000-0002", 100L, 10L, 150_000, "1111053000",
                "1111060000", "04524", "06236");

        Object serialized = eventSerializer.serialize(event);

        assertThat(eventSerializer.deserialize(serialized, OrderConfirmed.class)).isEqualTo(event);
    }
}
