package setty.estimate.application.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import setty.estimate.domain.EstimateRequest;

@DisplayName("견적 요청 생성 이벤트")
class EstimateRequestCreatedEventTest {
    private static final String NAME = "테스트사용자";
    private static final String PHONE_NUMBER = "01000000000";
    private static final String TRADE_AREA = "서울성북구";

    @Test
    @DisplayName("신청자 이름·연락처·거래 지역을 담지 않는다")
    void doesNotCarryPersonalInformation() {
        final EstimateRequestCreatedEvent event = EstimateRequestCreatedEvent.from(estimateRequest());

        assertThat(event.toString())
                .doesNotContain(NAME)
                .doesNotContain(PHONE_NUMBER)
                .doesNotContain(TRADE_AREA);
    }

    @Test
    @DisplayName("물품과 게시물 링크, 접수 시각만 담는다")
    void carriesItemAndCreatedAtOnly() {
        final EstimateRequest estimateRequest = estimateRequest();

        final EstimateRequestCreatedEvent event = EstimateRequestCreatedEvent.from(estimateRequest);

        assertThat(event.itemType()).isEqualTo("원목의자");
        assertThat(event.highValueItem()).isFalse();
        assertThat(event.productLink()).isEqualTo("https://www.daangn.com/articles/test-1");
        assertThat(event.createdAt()).isEqualTo(estimateRequest.getCreatedAt());
    }

    private EstimateRequest estimateRequest() {
        return EstimateRequest.pendingReview(
                NAME,
                PHONE_NUMBER,
                TRADE_AREA,
                "원목의자",
                false,
                "https://www.daangn.com/articles/test-1"
        );
    }
}
