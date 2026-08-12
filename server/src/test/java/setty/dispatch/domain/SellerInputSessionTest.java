package setty.dispatch.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import setty.dispatch.exception.SellerInputAlreadySubmittedException;

@DisplayName("판매자 입력 세션")
class SellerInputSessionTest {
    private static SellerInputSession newSession() {
        return new SellerInputSession("session-token", new DispatchRequest(
                "buyer-token",
                "테스트구매자",
                "010-0000-0001",
                "서울특별시 테스트구 테스트로 1",
                "책상",
                false,
                "https://www.daangn.com/articles/test-1",
                java.util.List.of(),
                null
        ));
    }

    private static SellerInput newSellerInput() {
        return new SellerInput(
                "테스트판매자",
                "010-0000-0002",
                "서울특별시 테스트구 테스트로 2",
                "평일 오후"
        );
    }

    @Test
    @DisplayName("발급되면 아직 사용되지 않은 상태다")
    void startsPending() {
        assertThat(newSession().isCompleted()).isFalse();
    }

    @Test
    @DisplayName("세션을 완료하면 배차 요청도 함께 최종 검토 대기로 넘어간다")
    void completingSessionAlsoMovesDispatchRequestForward() {
        final SellerInputSession session = newSession();

        session.complete(newSellerInput());

        assertThat(session.isCompleted()).isTrue();
        assertThat(session.getDispatchRequest().getStatus()).isEqualTo(DispatchStatus.FINAL_REVIEW_PENDING);
    }

    @Test
    @DisplayName("이미 완료된 세션은 다시 사용할 수 없다")
    void rejectsReuseOfACompletedSession() {
        final SellerInputSession session = newSession();
        session.complete(newSellerInput());

        assertThatThrownBy(() -> session.complete(newSellerInput()))
                .isInstanceOf(SellerInputAlreadySubmittedException.class);
    }
}
