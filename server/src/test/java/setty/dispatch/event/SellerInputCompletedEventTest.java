package setty.dispatch.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import setty.dispatch.domain.DispatchRequest;
import setty.dispatch.domain.SellerInput;

@DisplayName("판매자 입력 완료 이벤트")
class SellerInputCompletedEventTest {
    private static final String BUYER_NAME = "테스트구매자";
    private static final String BUYER_PHONE_NUMBER = "01000000001";
    private static final String DELIVERY_ADDRESS = "서울특별시 테스트구 테스트로 1";
    private static final String SELLER_NAME = "테스트판매자";
    private static final String SELLER_PHONE_NUMBER = "01000000002";
    private static final String PICKUP_ADDRESS = "서울특별시 테스트구 테스트로 2";
    private static final String ITEM_IMAGE_URL =
            "https://techcourse-project-2026.s3.ap-northeast-2.amazonaws.com/setty/images/items/test-1.jpg";

    @Test
    @DisplayName("구매자·판매자 개인정보와 물품 사진 URL을 담지 않는다")
    void doesNotCarryPersonalInformation() {
        final SellerInputCompletedEvent event = SellerInputCompletedEvent.from(sellerInputCompletedRequest());

        assertThat(event.toString())
                .doesNotContain(BUYER_NAME)
                .doesNotContain(BUYER_PHONE_NUMBER)
                .doesNotContain(DELIVERY_ADDRESS)
                .doesNotContain(SELLER_NAME)
                .doesNotContain(SELLER_PHONE_NUMBER)
                .doesNotContain(PICKUP_ADDRESS)
                .doesNotContain(ITEM_IMAGE_URL);
    }

    @Test
    @DisplayName("구매자 접수 시각과 판매자 입력 완료 시각을 함께 담는다")
    void carriesBothTimes() {
        final DispatchRequest dispatchRequest = sellerInputCompletedRequest();

        final SellerInputCompletedEvent event = SellerInputCompletedEvent.from(dispatchRequest);

        assertThat(event.createdAt()).isEqualTo(dispatchRequest.getCreatedAt());
        assertThat(event.sellerInputCompletedAt()).isEqualTo(dispatchRequest.getSellerInputCompletedAt());
        assertThat(event.itemImageCount()).isEqualTo(1);
        assertThat(event.itemType()).isEqualTo("책상");
    }

    private DispatchRequest sellerInputCompletedRequest() {
        final DispatchRequest dispatchRequest = new DispatchRequest(
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
        dispatchRequest.completeSellerInput(
                new SellerInput(SELLER_NAME, SELLER_PHONE_NUMBER, PICKUP_ADDRESS, "평일 오후")
        );

        return dispatchRequest;
    }
}
