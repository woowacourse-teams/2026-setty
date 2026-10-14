package setty.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import setty.support.MySqlIntegrationTestSupport;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
class OrderConfirmedSerializationTest extends MySqlIntegrationTestSupport {

    // 이벤트 발행 기록은 앱의 ObjectMapper로 저장·복원된다(spring-modulith-events-jackson)
    @Autowired
    private ObjectMapper objectMapper;

    // 배포 전에 저장된 미완료 이벤트가 재발행될 때 새 필드가 없어도 읽혀야 한다
    @Test
    void 배송지_행정동이_없는_예전_이벤트도_읽고_행정동은_null이다() {
        String legacy = """
                {"orderId":1,"itemName":"가상 책상","category":"DESK","pickupAddress":"판매자 주소",
                 "deliveryAddress":"구매자 주소","deliveryFee":10000,"pickupPhoneNumber":"010-0000-0001",
                 "deliveryPhoneNumber":"010-0000-0002","listingId":100,"sellerId":10,"itemPrice":150000}
                """;

        OrderConfirmed event = objectMapper.readValue(legacy, OrderConfirmed.class);

        assertThat(event.deliveryDongCode()).isNull();
        assertThat(event.deliveryFee()).isEqualTo(10_000);
        assertThat(event.deliveryAddress()).isEqualTo("구매자 주소");
    }

    @Test
    void 배송지_행정동을_담아_저장하고_다시_읽는다() {
        OrderConfirmed event = new OrderConfirmed(1L, "가상 책상", "DESK", "판매자 주소", "구매자 주소", 25_000,
                "010-0000-0001", "010-0000-0002", 100L, 10L, 150_000, "1111053000");

        String serialized = objectMapper.writeValueAsString(event);

        assertThat(objectMapper.readValue(serialized, OrderConfirmed.class)).isEqualTo(event);
    }
}
