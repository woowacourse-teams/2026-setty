package setty.dispatch.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import setty.dispatch.domain.DispatchRequest;

@DisplayName("배차 요청 생성 이벤트")
class DispatchRequestCreatedEventTest {
    private static final String BUYER_NAME = "테스트구매자";
    private static final String BUYER_PHONE_NUMBER = "01000000001";
    private static final String DELIVERY_ADDRESS = "서울특별시 테스트구 테스트로 1";
    private static final String ITEM_IMAGE_URL =
            "https://techcourse-project-2026.s3.ap-northeast-2.amazonaws.com/setty/images/items/test-1.jpg";

    @Test
    @DisplayName("구매자 개인정보와 물품 사진 URL을 담지 않는다")
    void doesNotCarryPersonalInformation() {
        final DispatchRequest dispatchRequest = dispatchRequest();

        final DispatchRequestCreatedEvent event = DispatchRequestCreatedEvent.from(dispatchRequest);

        assertThat(event.toString())
                .doesNotContain(BUYER_NAME)
                .doesNotContain(BUYER_PHONE_NUMBER)
                .doesNotContain(DELIVERY_ADDRESS)
                .doesNotContain(ITEM_IMAGE_URL);
    }

    @Test
    @DisplayName("물품 사진은 개수만 담는다")
    void carriesItemImageCountOnly() {
        final DispatchRequest dispatchRequest = dispatchRequest();

        final DispatchRequestCreatedEvent event = DispatchRequestCreatedEvent.from(dispatchRequest);

        assertThat(event.itemImageCount()).isEqualTo(1);
        assertThat(event.itemType()).isEqualTo("책상");
        assertThat(event.highValueItem()).isFalse();
        assertThat(event.productLink()).isEqualTo("https://www.daangn.com/articles/test-1");
        assertThat(event.createdAt()).isEqualTo(dispatchRequest.getCreatedAt());
    }

    private DispatchRequest dispatchRequest() {
        return new DispatchRequest(
                "test-buyer-token",
                BUYER_NAME,
                BUYER_PHONE_NUMBER,
                DELIVERY_ADDRESS,
                "책상",
                false,
                "https://www.daangn.com/articles/test-1",
                List.of(ITEM_IMAGE_URL),
                null
        );
    }
}
